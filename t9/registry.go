//go:build windows

package main

import (
	"unsafe"
)

const (
	hkeyCurrentUser = uintptr(0x80000001)
	keySetValue     = 0x0002
	keyReadValue    = 0x00020000
	regSZ           = 1
)

func regOpenRunKey(sam uint32) (uintptr, error) {
	var h uintptr
	sub := toUTF16(runKeySub)
	r, _, err := procRegOpenKeyExW.Call(
		hkeyCurrentUser,
		uintptr(unsafe.Pointer(sub)),
		0,
		uintptr(sam),
		uintptr(unsafe.Pointer(&h)),
	)
	if r != 0 {
		return 0, err
	}
	return h, nil
}

// setRunKey прописывает HKCU\...\Run\<appName> = "<path>" —
// именно благодаря этому программа загружается с системой.
func setRunKey(path string) error {
	h, err := regOpenRunKey(keySetValue)
	if err != nil {
		return err
	}
	defer procRegCloseKey.Call(h)
	name := toUTF16(appName)
	val := syscall.StringToUTF16(`"` + path + `"`) // []uint16, включая NUL
	r, _, err := procRegSetValueExW.Call(
		h,
		uintptr(unsafe.Pointer(name)),
		0,
		regSZ,
		uintptr(unsafe.Pointer(val)),
		uintptr(len(val)*2),
	)
	if r != 0 {
		return err
	}
	return nil
}

func deleteRunKey() error {
	h, err := regOpenRunKey(keySetValue)
	if err != nil {
		return err
	}
	defer procRegCloseKey.Call(h)
	name := toUTF16(appName)
	r, _, err := procRegDeleteValueW.Call(h, uintptr(unsafe.Pointer(name)))
	if r != 0 {
		return err
	}
	return nil
}

func runKeySet() bool {
	h, err := regOpenRunKey(keyReadValue)
	if err != nil {
		return false
	}
	defer procRegCloseKey.Call(h)
	name := toUTF16(appName)
	var sz uint32
	r, _, _ := procRegQueryValueExW.Call(
		h, uintptr(unsafe.Pointer(name)), 0, 0, 0, uintptr(unsafe.Pointer(&sz)),
	)
	if r != 0 {
		return false
	}
	if sz == 0 {
		return false
	}
	buf := make([]uint16, sz/2)
	r, _, _ = procRegQueryValueExW.Call(
		h,
		uintptr(unsafe.Pointer(name)),
		0,
		0,
		uintptr(unsafe.Pointer(&buf[0])),
		uintptr(unsafe.Pointer(&sz)),
	)
	return r == 0
}
