//go:build windows

package main

import (
	"strings"
	"time"
	"unicode"
	"unsafe"

	"syscall"
)

const (
	// maxBurstLen — больше такого «черновика» не накапливаем.
	maxBurstLen = 24
	// predictMaxLen — предсказание дороже, длиннее не пытаемся.
	predictMaxLen = 16
)

// hookCallback — C-совместимая процедура глобального low-level хука клавиатуры.
var hookCallback = syscall.NewCallback(llHookProc)

func (a *App) installHook() error {
	r, _, err := procSetWindowsHookExW.Call(WH_KEYBOARD_LL, hookCallback, 0, 0)
	if r == 0 {
		return err
	}
	a.hookID = r
	return nil
}

func llHookProc(code int32, wParam, lParam uintptr) uintptr {
	if code == HC_ACTION {
		ks := (*kbdHookStruct)(unsafe.Pointer(lParam))
		if app != nil && app.handleKeyEvent(ks.vkCode, ks.scanCode, ks.flags) {
			return 1 // «поглотили»: оригинальное нажатие не дойдёт до приложения
		}
	}
	procCallNextHookEx.Call(0, uintptr(code), wParam, lParam)
	return 0
}

// keyDown — нажата ли клавиша прямо сейчас (глобальное состояние клавиш).
// Нужен, потому что в flags хука нет состояний Ctrl/Shift — только
// LLKHF_EXTENDED/INJECTED/ALTDOWN/UP.
func keyDown(vk uint32) bool {
	v, _, _ := procGetKeyState.Call(uintptr(int32(vk)))
	return int16(v)&0x8000 != 0
}

// handleKeyEvent обрабатывает одно нажатие клавиши (только «вниз»).
// Возвращает true, если клавиша должна быть поглочена.
func (a *App) handleKeyEvent(vk, scan, flags uint32) bool {
	a.mu.Lock()
	defer a.mu.Unlock()

	// Игнорируем собственные (впрыскиваемые) клавиши и ВСЕ отпускания:
	// иначе каждый «key up» добавит букву в черновик ещё раз.
	if flags&(LLKHF_INJECTED|LLKHF_UP) != 0 {
		return false
	}

	ctrl := keyDown(VK_CONTROL)
	alt := flags&LLKHF_ALTDOWN != 0 || keyDown(VK_MENU)

	// Backspace стирает последний символ «черновика» (сам символ при этом
	// удаляется из поля обычным backspace — оставляем клавишу).
	if vk == VK_BACK {
		if ctrl || alt {
			a.buf = nil // Ctrl+Backspace удаляет слово целиком — не проследим.
		} else if n := len(a.buf); n > 0 {
			a.buf = a.buf[:n-1]
		}
		return false
	}

	if !a.t9On {
		a.buf = nil
		return false
	}

	// Комбинации с Ctrl/Alt — шорткаты приложений, не буквы слова.
	// Win/Alt сами по себе — уход от текста.
	if ctrl || alt || vk == VK_MENU || vk == VK_LWIN || vk == VK_RWIN {
		a.buf = nil
		return false
	}

	switch vk {
	case VK_TAB, VK_ESCAPE:
		a.buf = nil
		return false
	case VK_SPACE:
		return a.finalizeSeparator(' ')
	case VK_RETURN:
		return a.finalizeSeparator('\n')
	}

	ch, ok := a.charFor(vk, scan)
	if !ok {
		return false
	}
	if unicode.IsLetter(ch) {
		now := time.Now()
		if n := len(a.buf); n > 0 && now.Sub(a.lastKeyAt) > a.gapTime() {
			// Пауза: предыдущее слово завершаем, начинаем новое.
			a.finishBurst(0)
			a.buf = nil
		}
		if len(a.buf) >= maxBurstLen {
			a.buf = nil
			return false
		}
		a.buf = append(a.buf, ch)
		a.lastKeyAt = now
		return false
	}

	// Видимый небуквенный символ (знак препинания, цифра): завершаем
	// слово и отправляем этот символ следом.
	return a.finalizeSeparator(ch)
}

// finalizeSeparator завершает слово разделителем. Если замена произошла —
// клавиша поглатывается (слово и разделитель отправляются сами).
func (a *App) finalizeSeparator(sep rune) bool {
	if len(a.buf) >= a.minLen() && a.finishBurst(sep) {
		a.buf = nil
		return true
	}
	a.buf = nil
	return false
}

// finishBurst подбирает предсказание и отправляет замену.
// Вызывать с удерживаемым a.mu.
func (a *App) finishBurst(sep rune) bool {
	n := len(a.buf)
	if n < a.minLen() || n > predictMaxLen {
		return false
	}
	typed := strings.ToLower(string(a.buf))
	pred := a.engine.Predict(a.lang, typed)
	if pred == "" || pred == typed {
		return false // нет кандидата или набранное уже целое слово
	}
	out := ApplyCase(string(a.buf), pred)
	if out == string(a.buf) {
		return false
	}
	if err := replaceWord(string(a.buf), out, sep); err != nil {
		logf("ошибка отправки замены: %v", err)
		a.buf = nil
		return false
	}
	logf("T9: %q -> %q", typed, out)
	return true
}

// gapTime — пауза, после которой слово завершается автоматически.
func (a *App) gapTime() time.Duration {
	return time.Duration(a.cfg.GapMs) * time.Millisecond
}

// minLen — минимальная длина набираемого слова.
func (a *App) minLen() int {
	return a.cfg.MinLen
}

// gapTicker автоматически завершает слово, если пользователь замолчал.
func (a *App) gapTicker() {
	t := time.NewTicker(100 * time.Millisecond)
	defer t.Stop()
	for range t.C {
		// Запрос на выход при обновлении (его ставит selfInstall).
		if quitRequested() {
			procPostMessageW.Call(a.winHWND, WM_DESTROY, 0, 0)
			return
		}
		a.mu.Lock()
		if a.t9On && len(a.buf) >= a.minLen() && time.Since(a.lastKeyAt) > a.gapTime() {
			a.finishBurst(0)
			a.buf = nil
		}
		a.mu.Unlock()
	}
}

// charFor определяет символ, набранный физической клавишей, в раскладке
// активного окна (нужно, чтобы VK A-Z в русской раскладке давал «ф», «ц», …).
// Реальное состояние Shift/CapsLock берём через GetKeyState — в flags
// хука этих состояний нет.
func (a *App) charFor(vk, scan uint32) (rune, bool) {
	hFore, _, _ := procGetForegroundWindow.Call()
	if hFore == 0 {
		return 0, false
	}
	tu, _, _ := procGetWindowThreadProcessId.Call(hFore, 0)
	hkl, _, _ := procGetKeyboardLayout.Call(uintptr(uint32(tu)))

	state := [256]byte{}
	if keyDown(VK_SHIFT) {
		state[VK_SHIFT] = 0xFF
	}
	if keyDown(VK_CONTROL) {
		state[VK_CONTROL] = 0xFF
		state[0xA2] = 0xFF
		state[0xA3] = 0xFF
	}
	if keyDown(VK_MENU) {
		state[VK_MENU] = 0xFF
		state[0xA4] = 0xFF
		state[0xA5] = 0xFF
	}
	if c, _, _ := procGetKeyState.Call(uintptr(VK_CAPITAL)); int16(c)&1 != 0 {
		state[VK_CAPITAL] = 1
	}

	var ch [2]uint16
	n, _, _ := procToUnicodeEx.Call(
		1, // Translate=TRUE
		uintptr(vk),
		uintptr(scan),
		uintptr(unsafe.Pointer(&state)),
		uintptr(unsafe.Pointer(&ch)),
		2,
		0,
		hkl,
	)
	if n < 1 || ch[0] == 0 {
		return 0, false
	}
	return rune(ch[0]), true
}
