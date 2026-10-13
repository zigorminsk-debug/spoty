//go:build windows

package main

import (
	"encoding/binary"
	"unsafe"
)

// --- Иконка трея -----------------------------------------------------------

// iconBytes собирает ICO-файл (32x32, 32 бита) с цветным кругом.
func iconBytes(r, g, b byte) []byte {
	const size = 32
	plane := makeIconPlane(size, r, g, b)
	buf := make([]byte, 0, 22+len(plane))
	buf = append(buf,
		0, 0, // reserved
		1, 0, // type: icon
		1, 0, // count
		byte(size), byte(size), // width, height
		0, 0, // colors in palette
		1, 0, // color planes
		32, 0, // bits per pixel
	)
	var tmp [4]byte
	binary.LittleEndian.PutUint32(tmp[:], uint32(len(plane)))
	buf = append(buf, tmp[:]...)
	binary.LittleEndian.PutUint32(tmp[:], 22) // offset of pixel data
	buf = append(buf, tmp[:]...)
	buf = append(buf, plane...)
	return buf
}

func makeIconPlane(size int, r, g, b byte) []byte {
	hdr := make([]byte, 40) // BITMAPINFOHEADER
	binary.LittleEndian.PutUint32(hdr[0:4], 40)
	binary.LittleEndian.PutUint32(hdr[4:8], uint32(size))
	binary.LittleEndian.PutUint32(hdr[8:12], uint32(size*2)) // XOR + AND mask
	binary.LittleEndian.PutUint16(hdr[12:14], 1)
	binary.LittleEndian.PutUint16(hdr[14:16], 32)
	// biCompression = 0 (BI_RGB)

	row := size * 4
	maskRow := (size + 31) / 32 * 4
	buf := make([]byte, 0, 40+size*row+size*maskRow)
	buf = append(buf, hdr...)

	c := size / 2
	rad := size / 2 - 2
	for y := 0; y < size; y++ {
		for x := 0; x < size; x++ {
			dx, dy := x-c, y-c
			if dx*dx+dy*dy <= rad*rad {
				buf = append(buf, b, g, r, 255) // BGRA
			} else {
				buf = append(buf, 0, 0, 0, 0) // прозрачный
			}
		}
	}
	buf = append(buf, make([]byte, size*maskRow)...) // AND-маска: всё непрозрачно
	return buf
}

func createIconFromBytes(data []byte) uintptr {
	h, _, _ := procCreateIconFromResourceEx.Call(
		uintptr(unsafe.Pointer(&data[0])),
		uintptr(len(data)),
		1, // fIcon = TRUE
		0x00030000,
		32,
		32,
		0, // LR_DEFAULTCOLOR
	)
	return h
}

// --- Трей -------------------------------------------------------------------

func (a *App) newNID() *notifyIconData {
	n := &notifyIconData{}
	n.cbSize = uint32(unsafe.Sizeof(notifyIconData{}))
	n.hWnd = a.winHWND
	n.uID = 1
	n.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP
	n.uCallbackMessage = trayMsg
	return n
}

func setNIDTip(n *notifyIconData, tip string) {
	w := toUTF16(tip)
	if len(w) > 128 {
		w = w[:128]
	}
	copy(n.szTip[:len(w)], w)
}

func (a *App) trayTip() string {
	a.mu.Lock()
	on, hk := a.t9On, a.cfg.HotKey
	a.mu.Unlock()
	if on {
		return "Spoty T9: вкл (" + hk + ")"
	}
	return "Spoty T9: выкл (" + hk + ")"
}

func (a *App) addTrayIcon() {
	if a.winHWND == 0 {
		return
	}
	a.mu.Lock()
	on := a.t9On
	a.mu.Unlock()
	n := a.newNID()
	if on {
		n.hIcon = a.iconOn
	} else {
		n.hIcon = a.iconOff
	}
	setNIDTip(n, a.trayTip())
	ok, _, err := procShellNotifyIconW.Call(NIM_ADD, uintptr(unsafe.Pointer(n)))
	if ok == 0 {
		logf("Shell_NotifyIconW(NIM_ADD): %v", err)
	}
}

func (a *App) removeTrayIcon() {
	if a.winHWND == 0 {
		return
	}
	n := a.newNID()
	procShellNotifyIconW.Call(NIM_DELETE, uintptr(unsafe.Pointer(n)))
}

func (a *App) updateTrayIcon() {
	if a.winHWND == 0 {
		return
	}
	a.mu.Lock()
	on := a.t9On
	a.mu.Unlock()
	n := a.newNID()
	if on {
		n.hIcon = a.iconOn
	} else {
		n.hIcon = a.iconOff
	}
	setNIDTip(n, a.trayTip())
	procShellNotifyIconW.Call(NIM_MODIFY, uintptr(unsafe.Pointer(n)))
}

// onTrayNotify — callback сообщения trayMsg (lParam = код события).
func (a *App) onTrayNotify(lParam uintptr) {
	switch uint32(lParam) {
	case NIN_POPUPMENU, WM_LBUTTONUP, WM_RBUTTONUP:
		a.showMenu()
	}
}

const (
	mnuToggle    = 101
	mnuLangAuto  = 201
	mnuLangRU    = 202
	mnuLangEN    = 203
	mnuAutostart = 301
	mnuLog       = 401
	mnuAbout     = 402
	mnuExit      = 403
)

func (a *App) showMenu() {
	a.mu.Lock()
	on, lang, hotKey := a.t9On, a.lang, a.cfg.HotKey
	a.mu.Unlock()
	auto := runKeySet()

	menu, _, _ := procCreatePopupMenu.Call()
	if menu == 0 {
		return
	}
	defer procDestroyPopupMenu.Call(menu)

	appendItem := func(text string, id uint32, check bool) {
		f := uintptr(MF_UNCHECKED)
		if check {
			f = MF_CHECKED
		}
		t := toUTF16(text)
		procAppendMenuW.Call(menu, f, uintptr(id), uintptr(unsafe.Pointer(t)))
	}
	sep := func() {
		procAppendMenuW.Call(menu, MF_SEPARATOR, 0, 0)
	}

	name := "Выключить T9 (" + hotKey + ")"
	if !on {
		name = "Включить T9 (" + hotKey + ")"
	}
	appendItem(name, mnuToggle, on)
	sep()
	appendItem("Язык: авто", mnuLangAuto, lang == LanguageAuto)
	appendItem("Язык: русский", mnuLangRU, lang == LanguageRU)
	appendItem("Язык: английский", mnuLangEN, lang == LanguageEN)
	sep()
	appendItem("Запускать вместе с Windows", mnuAutostart, auto)
	appendItem("Открыть журнал", mnuLog, false)
	appendItem("О программе", mnuAbout, false)
	sep()
	appendItem("Выход", mnuExit, false)

	var p point
	procGetCursorPos.Call(uintptr(unsafe.Pointer(&p)))
	procTrackPopupMenu.Call(menu, TPM_RIGHTALIGN, uintptr(p.x), uintptr(p.y), 0, a.winHWND, 0)
	procPostMessageW.Call(a.winHWND, WM_NULL, 0, 0)
}

func (a *App) onMenuCommand(id uint16) {
	switch id {
	case mnuToggle:
		a.toggleT9()
	case mnuLangAuto, mnuLangRU, mnuLangEN:
		var l Language
		switch id {
		case mnuLangRU:
			l = LanguageRU
		case mnuLangEN:
			l = LanguageEN
		default:
			l = LanguageAuto
		}
		a.setLanguage(l)
	case mnuAutostart:
		a.toggleAutostart()
	case mnuLog:
		openLog()
	case mnuAbout:
		msgBox(appTitle, aboutText())
	case mnuExit:
		a.exit()
	}
}
