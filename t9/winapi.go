//go:build windows

package main

import (
	"fmt"
	"unsafe"

	"syscall"
)

var (
	user32   = syscall.NewLazyDLL("user32.dll")
	kernel32 = syscall.NewLazyDLL("kernel32.dll")
	advapi   = syscall.NewLazyDLL("advapi32.dll")
)

var (
	procSetWindowsHookExW        = user32.NewProc("SetWindowsHookExW")
	procUnhookWindowsHookEx      = user32.NewProc("UnhookWindowsHookEx")
	procCallNextHookEx           = user32.NewProc("CallNextHookEx")
	procGetMessageW              = user32.NewProc("GetMessageW")
	procTranslateMessage         = user32.NewProc("TranslateMessage")
	procDispatchMessageW         = user32.NewProc("DispatchMessageW")
	procPostMessageW             = user32.NewProc("PostMessageW")
	procRegisterClassExW         = user32.NewProc("RegisterClassExW")
	procCreateWindowExW          = user32.NewProc("CreateWindowExW")
	procDefWindowProcW           = user32.NewProc("DefWindowProcW")
	procRegisterHotKey           = user32.NewProc("RegisterHotKey")
	procUnregisterHotKey         = user32.NewProc("UnregisterHotKey")
	procCreatePopupMenu          = user32.NewProc("CreatePopupMenu")
	procAppendMenuW              = user32.NewProc("AppendMenuW")
	procTrackPopupMenu           = user32.NewProc("TrackPopupMenu")
	procDestroyPopupMenu         = user32.NewProc("DestroyPopupMenu")
	procSendInput                = user32.NewProc("SendInput")
	procVkKeyScanW               = user32.NewProc("VkKeyScanW")
	procMessageBoxW              = user32.NewProc("MessageBoxW")
	procShellNotifyIconW         = user32.NewProc("Shell_NotifyIconW")
	procCreateIconFromResourceEx = user32.NewProc("CreateIconFromResourceEx")
	procToUnicodeEx              = user32.NewProc("ToUnicodeEx")
	procGetKeyboardLayout        = user32.NewProc("GetKeyboardLayout")
	procGetWindowThreadProcessId = user32.NewProc("GetWindowThreadProcessId")
	procGetForegroundWindow      = user32.NewProc("GetForegroundWindow")
	procRegisterWindowMessageW   = user32.NewProc("RegisterWindowMessageW")
	procGetCursorPos             = user32.NewProc("GetCursorPos")
	procLoadCursorW              = user32.NewProc("LoadCursorW")
	procGetKeyState              = user32.NewProc("GetKeyState")
	procGetModuleHandleW         = kernel32.NewProc("GetModuleHandleW")
	procAllocConsole             = kernel32.NewProc("AllocConsole")
	procCreateMutexW             = kernel32.NewProc("CreateMutexW")

	procRegOpenKeyExW    = advapi.NewProc("RegOpenKeyExW")
	procRegSetValueExW   = advapi.NewProc("RegSetValueExW")
	procRegQueryValueExW = advapi.NewProc("RegQueryValueExW")
	procRegDeleteValueW  = advapi.NewProc("RegDeleteValueW")
	procRegCloseKey      = advapi.NewProc("RegCloseKey")
)

const (
	WM_DESTROY   = 0x0002
	WM_NULL      = 0x0000
	WM_COMMAND   = 0x0111
	WM_HOTKEY    = 0x0312
	WM_QUIT      = 0x0012
	WM_APP       = 0x8000
	WM_LBUTTONUP = 0x0202
	WM_RBUTTONUP = 0x0205

	// trayMsg — собственное сообщение о событиях трей-иконки.
	trayMsg = WM_APP + 1

	HC_ACTION = 0

	WH_KEYBOARD_LL = 13

	// Флаги KBDLLHOOKSTRUCT (есть только они!).
	LLKHF_EXTENDED = 0x00000001
	LLKHF_INJECTED = 0x00000010
	LLKHF_ALTDOWN  = 0x00000020
	LLKHF_UP       = 0x80000000

	VK_BACK    = 0x08
	VK_TAB     = 0x09
	VK_RETURN  = 0x0D
	VK_SHIFT   = 0x10
	VK_CONTROL = 0x11
	VK_MENU    = 0x12
	VK_CAPITAL = 0x14
	VK_SPACE   = 0x20
	VK_ESCAPE  = 0x1B
	VK_LWIN    = 0x5B
	VK_RWIN    = 0x5C

	// Модификаторы RegisterHotKey.
	MOD_ALT      = 0x0001
	MOD_CONTROL  = 0x0002
	MOD_SHIFT    = 0x0004
	MOD_NOREPEAT = 0x40000000

	// Флаги меню.
	MF_UNCHECKED = 0x0000
	MF_CHECKED   = 0x0008
	MF_SEPARATOR = 0x0000

	TPM_RIGHTALIGN = 0x0008

	// Уведомления Shell_NotifyIconW.
	NIM_ADD       = 0x0000
	NIM_MODIFY    = 0x0001
	NIM_DELETE    = 0x0002
	NIF_MESSAGE   = 0x0001
	NIF_ICON      = 0x0002
	NIF_TIP       = 0x0004
	NIN_POPUPMENU = 0

	// SendInput.
	INPUT_KEYBOARD  = 1
	KEYEVENTF_KEYUP = 0x0002

	IDC_ARROW = 32512

	MB_ICONINFORMATION = 0x0040

	// Родитель для message-only окна.
	HWND_MESSAGE = ^uintptr(2)
)

type point struct {
	x, y int32
}

type msg struct {
	hwnd    uintptr
	message uint32
	wParam  uintptr
	lParam  uintptr
	time    uint32
	pt      point
}

type wndClassEx struct {
	cbSize        uint32
	style         uint32
	lpfnWndProc   uintptr
	cbClsExtra    int32
	cbWndExtra    int32
	hInstance     uintptr
	hIcon         uintptr
	hCursor       uintptr
	hbrBackground uintptr
	lpszMenuName  *uint16
	lpszClassName *uint16
	hIconSm       uintptr
}

type kbdHookStruct struct {
	vkCode      uint32
	scanCode    uint32
	flags       uint32
	time        uint32
	dwExtraInfo uintptr
}

type keybdInput struct {
	wVk         uint16
	wScan       uint16
	dwFlags     uint32
	time        uint32
	dwExtraInfo uintptr
}

type input struct {
	typ uint32
	_   uint32
	ki  keybdInput
}

// notifyIconData — NOTIFYICONDATAW (Windows 10, 968 байт).
type notifyIconData struct {
	cbSize           uint32
	hWnd             uintptr
	uID              uint32
	uFlags           uint32
	uCallbackMessage uint32
	hIcon            uintptr
	szTip            [128]uint16
	dwState          uint32
	dwStateMask      uint32
	szInfo           [256]uint16
	uVersion         uint32
	szInfoTitle      [64]uint16
	dwInfoFlags      uint32
	guidItem         [16]byte
}

func toUTF16(s string) *uint16 {
	return syscall.StringToUTF16Ptr(s)
}

func msgBox(title, text string) {
	t := toUTF16(title)
	m := toUTF16(text)
	procMessageBoxW.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), MB_ICONINFORMATION)
}

// keyInput — одно клавиатурное событие SendInput.
func keyInput(vk uint16, scan uint16, flags uint32) input {
	return input{
		typ: INPUT_KEYBOARD,
		ki:  keybdInput{wVk: vk, wScan: scan, dwFlags: flags},
	}
}

func sendInput(ins []input) error {
	if len(ins) == 0 {
		return nil
	}
	n, _, err := procSendInput.Call(
		uintptr(len(ins)),
		uintptr(unsafe.Pointer(&ins[0])),
		uintptr(unsafe.Sizeof(ins[0])),
	)
	if n != uintptr(len(ins)) {
		if err != nil && err != syscall.Errno(0) {
			return err
		}
		return fmt.Errorf("SendInput: вставлено %d из %d событий", n, len(ins))
	}
	return nil
}

// vkKeyScan переводит символ в код виртуальной клавиши согласно текущей
// раскладке (необходимо, чтобы кириллица отправлялась правильными VK).
func vkKeyScan(ch rune) (vk uint16, needShift bool, ok bool) {
	v, _, _ := procVkKeyScanW.Call(uintptr(ch))
	if int32(v) == -1 {
		return 0, false, false
	}
	return uint16(v & 0xFF), v&0x100 != 0, true
}

// charInputs собирает события «нажатие+отпускание» для каждого символа,
// при необходимости зажигая Shift (заглавные буквы, знаки).
func charInputs(s string) []input {
	var ins []input
	for _, ch := range s {
		vk, needShift, ok := vkKeyScan(ch)
		if !ok {
			continue // символа нет в активной раскладке — пропускаем
		}
		if needShift {
			ins = append(ins, keyInput(VK_SHIFT, 0, 0))
		}
		ins = append(ins, keyInput(vk, 0, 0), keyInput(vk, 0, KEYEVENTF_KEYUP))
		if needShift {
			ins = append(ins, keyInput(VK_SHIFT, 0, KEYEVENTF_KEYUP))
		}
	}
	return ins
}

// replaceWord стирает набранный «черновик» (backspace-ами), печатает
// предсказанное слово и, если задан, отсылаемый разделитель.
// sep: 0 — без разделителя (автозавершение по паузе), '\n' — Enter,
// любое другое значение — печатный символ.
func replaceWord(typed, word string, sep rune) error {
	var ins []input
	for range typed {
		ins = append(ins, keyInput(VK_BACK, 0, 0), keyInput(VK_BACK, 0, KEYEVENTF_KEYUP))
	}
	ins = append(ins, charInputs(word)...)
	if sep != 0 {
		if sep == '\n' {
			ins = append(ins, keyInput(VK_RETURN, 0, 0), keyInput(VK_RETURN, 0, KEYEVENTF_KEYUP))
		} else {
			ins = append(ins, charInputs(string(sep))...)
		}
	}
	return sendInput(ins)
}
