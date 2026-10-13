//go:build windows

package main

import (
	"errors"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
	"unsafe"

	"syscall"
)

const (
	appName   = "SpotyT9"
	exeName   = "spoty-t9.exe"
	appTitle  = "Spoty T9"
	version   = "1.0.0"
	winClass  = "SpotyT9MessageWindow"
	runKeySub = `Software\Microsoft\Windows\CurrentVersion\Run`
	mutexName = `Local\SpotyT9`
)

// App — состояние приложения.
type App struct {
	mu sync.Mutex

	engine *Engine
	cfg    Config

	winHWND   uintptr
	iconOn   uintptr
	iconOff  uintptr
	taskbarMsg uint32
	hookID     uintptr

	// Черновик набираемого слова (под защитой mu).
	buf       []rune
	lastKeyAt time.Time

	// Настройки, меняемые в процессе работы (под защитой mu).
	t9On bool
	lang Language
}

// app — глобальный экземпляр (доступ из wndProc/hook).
var app *App

var instanceMutex syscall.Handle

func main() {
	_ = flag.Bool("install", false, "скопировать в %LOCALAPPDATA% и включить автозапуск")
	uninstallFlag := flag.Bool("uninstall", false, "отключить автозапуск и выйти")
	reexecFlag := flag.Bool("reexec", false, "внутренний флаг: процесс только что перезапущен")
	versionFlag := flag.Bool("version", false, "напечатать версию и выйти")
	flag.Parse()

	if *versionFlag {
		fmt.Println(appTitle, version)
		return
	}

	initLog()

	localAppData := os.Getenv("LOCALAPPDATA")
	if localAppData == "" {
		msgBox(appTitle, "Не определена переменная окружения %LOCALAPPDATA%.")
		return
	}
	installDir := filepath.Join(localAppData, "SpotyT9")
	installExe := filepath.Join(installDir, exeName)

	// Если работаем из «чужого» места (папка загрузки, go run, флешка) —
	// копируем себя в LOCALAPPDATA, включаем автозапуск и перезапускаемся.
	final := *reexecFlag
	if cur, err := os.Executable(); err == nil {
		final = final || filepath.Equal(filepath.Dir(cur), installDir)
		if !final {
			if err := selfInstall(cur, installDir, installExe); err != nil {
				msgBox(appTitle, "Не удалось установить: "+err.Error())
				logf("selfInstall: %v", err)
				return
			}
		}
	} // selfInstall не возвращает управление

	// Автозапуск включаем всегда (идемпоентно): программа «загружается
	// с системой» сразу после первого запуска.
	if err := setRunKey(installExe); err != nil {
		logf("setRunKey: %v", err)
	}

	if *uninstallFlag {
		if err := deleteRunKey(); err != nil {
			msgBox(appTitle, "Не удалось отключить автозапуск: "+err.Error())
		} else {
			msgBox(appTitle,
				"Автозапуск Spoty T9 отключён.\n\n"+
					"Файлы программы: %LOCALAPPDATA%\\SpotyT9\n"+
					"(папку можно удалить вручную, если она больше не нужна)")
		}
		closeLog()
		return
	}

	// Итоговый процесс (сам запуск из папки установки или перезапуск после
	// установки) проверяет, что экземпляр один. Ретраи — родительский
	// процесс при selfInstall ещё может жить доли секунды.
	if final && !acquireInstanceMutexRetry(3 * time.Second) {
		msgBox(appTitle, "Spoty T9 уже запущен — значок в области уведомлений.")
		closeLog()
		return
	}

	a := NewApp()
	app = a
	a.run()
}

func NewApp() *App {
	cfg := loadConfig()
	dataOn := iconBytes(0x2E, 0xA0, 0x43)  // зелёный
	dataOff := iconBytes(0x9E, 0x9E, 0x9E) // серый
	return &App{
		engine:  newEngine(),
		cfg:     cfg,
		lang:    languageFromString(cfg.Language),
		t9On:    true,
		iconOn:  createIconFromBytes(dataOn),
		iconOff: createIconFromBytes(dataOff),
	}
}

func (a *App) run() {
	logf("запуск v%s (hotkey=%s, lang=%s)", version, a.cfg.HotKey, a.cfg.Language)
	a.initWindow()
	a.addTrayIcon()
	a.registerHotKey()
	if err := a.installHook(); err != nil {
		logf("SetWindowsHookExW: %v", err)
		msgBox(appTitle, "Не удалось установить хук клавиатуры: "+err.Error())
	}
	go a.gapTicker()

	var m msg
	for {
		r, _, _ := procGetMessageW.Call(uintptr(unsafe.Pointer(&m)), 0, 0, 0)
		if int32(r) == -1 || r == 0 {
			break // -1 — ошибка, 0 — WM_QUIT
		}
		procTranslateMessage.Call(uintptr(unsafe.Pointer(&m)))
		procDispatchMessageW.Call(uintptr(unsafe.Pointer(&m)))
	}
	a.cleanup()
}

// --- Окно -------------------------------------------------------------------

func (a *App) initWindow() {
	hInstance, _, _ := procGetModuleHandleW.Call(0)
	className := toUTF16(winClass)
	hCursor, _, _ := procLoadCursorW.Call(0, IDC_ARROW)
	wc := wndClassEx{
		cbSize:        uint32(unsafe.Sizeof(wndClassEx{})),
		lpfnWndProc:   syscall.NewCallback(wndProc),
		hInstance:     hInstance,
		hCursor:       hCursor,
		lpszClassName: className,
	}
	atom, _, err := procRegisterClassExW.Call(uintptr(unsafe.Pointer(&wc)))
	if atom == 0 {
		logf("RegisterClassExW: %v", err)
	}
	a.winHWND, _, err = procCreateWindowExW.Call(
		0, // dwExStyle
		uintptr(unsafe.Pointer(className)),
		0, // window name
		0, // style
		0, 0, 0, 0,
		HWND_MESSAGE, // message-only window: без записи в задачнике
		0,
		hInstance,
		0,
	)
	if a.winHWND == 0 {
		logf("CreateWindowExW: %v", err)
	}
	tm, _, _ := procRegisterWindowMessageW.Call(uintptr(unsafe.Pointer(toUTF16("TaskbarCreated"))))
	a.taskbarMsg = uint32(tm)
}

// wndProc — обработчик сообщения message-only окна.
func wndProc(hwnd uintptr, msgID uint32, wParam, lParam uintptr) uintptr {
	switch msgID {
	case WM_DESTROY:
		procPostMessageW.Call(hwnd, WM_QUIT, 0, 0)
		return 0
	case WM_HOTKEY:
		if wParam == 1 && app != nil {
			app.toggleT9()
		}
		return 0
	case WM_COMMAND:
		if app != nil {
			app.onMenuCommand(uint16(wParam))
		}
		return 0
	case WM_NULL:
		return 0
	}
	if app != nil {
		if msgID == trayMsg {
			app.onTrayNotify(lParam)
			return 0
		}
		if msgID == app.taskbarMsg { // Explorer перезапустился
			app.addTrayIcon()
			return 0
		}
	}
	r, _, _ := procDefWindowProcW.Call(hwnd, uintptr(msgID), wParam, lParam)
	return r
}

// --- Действия ----------------------------------------------------------------

func (a *App) toggleT9() {
	a.mu.Lock()
	a.t9On = !a.t9On
	if !a.t9On {
		a.buf = nil
	}
	on := a.t9On
	a.mu.Unlock()
	logf("T9 %s", map[bool]string{true: "вкл", false: "выкл"}[on])
	a.updateTrayIcon()
}

func (a *App) setLanguage(l Language) {
	a.mu.Lock()
	a.lang = l
	a.buf = nil
	a.cfg.Language = string(l)
	cfg := a.cfg
	a.mu.Unlock()
	saveConfig(cfg)
	logf("язык: %s", l)
	a.updateTrayIcon()
}

func (a *App) toggleAutostart() {
	exe, err := os.Executable()
	if err != nil {
		exe = filepath.Join(os.Getenv("LOCALAPPDATA"), "SpotyT9", exeName)
	}
	if runKeySet() {
		if err := deleteRunKey(); err != nil {
			logf("deleteRunKey: %v", err)
			return
		}
		logf("автозапуск: выключен")
	} else {
		if err := setRunKey(exe); err != nil {
			logf("setRunKey: %v", err)
			return
		}
		logf("автозапуск: включен")
	}
	a.updateTrayIcon()
}

func (a *App) exit() {
	a.removeTrayIcon()
	procPostMessageW.Call(a.winHWND, WM_DESTROY, 0, 0)
}

func (a *App) cleanup() {
	a.removeTrayIcon()
	if a.hookID != 0 {
		procUnhookWindowsHookEx.Call(a.hookID)
		a.hookID = 0
	}
	procUnregisterHotKey.Call(a.winHWND, 1)
	logf("остановка")
	closeLog()
}

// --- Горячая клавиша ----------------------------------------------------------

func parseHotKey(s string) (mods, vk uint32) {
	mods = MOD_NOREPEAT
	vk = 0x70 // F1 по умолчанию
	for _, part := range strings.Split(strings.ToUpper(strings.TrimSpace(s)), "+") {
		switch part {
		case "CTRL", "CONTROL":
			mods |= MOD_CONTROL
		case "ALT":
			mods |= MOD_ALT
		case "SHIFT":
			mods |= MOD_SHIFT
		case "SPACE":
			vk = VK_SPACE
		default:
			if len(part) == 1 && part[0] >= 'A' && part[0] <= 'Z' {
				vk = uint32(part[0])
			} else if len(part) >= 2 && part[0] == 'F' {
				if n, err := strconv.Atoi(part[1:]); err == nil && n >= 1 && n <= 24 {
					vk = uint32(0x6F + n) // F1 = 0x70
				}
			}
		}
	}
	return mods, vk
}

func (a *App) registerHotKey() {
	mods, vk := parseHotKey(a.cfg.HotKey)
	ok, _, err := procRegisterHotKey.Call(a.winHWND, 1, mods, vk)
	if ok == 0 {
		logf("RegisterHotKey(%s): %v", a.cfg.HotKey, err)
	}
}

// --- Установка / автозапуск ----------------------------------------------------

func selfInstall(cur, installDir, installExe string) (retErr error) {
	defer func() {
		if r := recover(); r != nil {
			retErr = fmt.Errorf("ошибка: %v", r)
		}
	}()
	if err := os.MkdirAll(installDir, 0o755); err != nil {
		return err
	}
	data, err := os.ReadFile(cur)
	if err != nil {
		return err
	}
	tmp := installExe + ".new"
	if err := os.WriteFile(tmp, data, 0o755); err != nil {
		return err
	}
	if err := os.Rename(tmp, installExe); err != nil {
		os.Remove(tmp)
		// Файл занят (программа запущена из этой папки) — пробуем заменить.
		if rmErr := os.Remove(installExe); rmErr != nil {
			return rmErr
		}
		if err2 := os.WriteFile(installExe, data, 0o755); err2 != nil {
			return err2
		}
	}
	if err := setRunKey(installExe); err != nil {
		return err
	}
	logf("установка: %s", installExe)
	cmd := exec.Command(installExe, "--reexec")
	cmd.Dir = installDir
	if err := cmd.Start(); err != nil {
		return err
	}
	os.Exit(0)
	return nil
}

func acquireInstanceMutexRetry(timeout time.Duration) bool {
	deadline := time.Now().Add(timeout)
	for {
		h, err := syscall.CreateMutexW(nil, false, toUTF16(mutexName))
		if h != 0 {
			instanceMutex = h
			if err == nil || !errors.Is(err, syscall.ERROR_ALREADY_EXISTS) {
				return true
			}
		} else {
			return true // не критично — работаем дальше
		}
		if time.Now().After(deadline) {
			return false
		}
		time.Sleep(100 * time.Millisecond)
	}
}

// --- Прочее -------------------------------------------------------------------

func openLog() {
	p := logPath()
	if p == "" {
		msgBox(appTitle, "Журнал недоступен.")
		return
	}
	if _, err := os.Stat(p); err == nil {
		if err := exec.Command("notepad", p).Start(); err != nil {
			msgBox(appTitle, "Не удалось открыть журнал: "+err.Error())
		}
	} else {
		msgBox(appTitle, "Журнал пока пуст (событий не было).")
	}
}

func aboutText() string {
	return "Spoty T9 v" + version + "\n\n" +
		"T9-ввод для Windows (русский и английский).\n" +
		"Включите T9 клавишей из значка (по умолчанию F9),\n" +
		"наберите первые буквы слова и нажмите пробел:\n\n" +
		"  «првт» + пробел  ->  «привет»\n" +
		"  «wrk»  + пробел  ->  «work»\n\n" +
		"Словарь: 20 000 самых частотных слов на язык.\n" +
		"Списки слов: hermitdave/FrequencyWords (MIT-лицензия)."
}
