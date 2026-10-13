//go:build windows

package main

import (
	"testing"
	"unsafe"
)

// Размеры наших структур должны совпадать с раскладкой Win32 (x64),
// иначе API-вызовы читают мусор.
func TestStructSizes(t *testing.T) {
	cases := []struct {
		name string
		got, want uintptr
	}{
		{"msg", unsafe.Sizeof(msg{}), 48},
		{"input", unsafe.Sizeof(input{}), 32},
		{"keybdInput", unsafe.Sizeof(keybdInput{}), 24},
		{"kbdHookStruct", unsafe.Sizeof(kbdHookStruct{}), 24},
		{"wndClassEx", unsafe.Sizeof(wndClassEx{}), 80},
		{"notifyIconData", unsafe.Sizeof(notifyIconData{}), 968},
		{"point", unsafe.Sizeof(point{}), 8},
	}
	for _, c := range cases {
		if c.got != c.want {
			t.Errorf("sizeof(%s) = %d, want %d", c.name, c.got, c.want)
		}
	}
}

func TestParseHotKey(t *testing.T) {
	mods, vk := parseHotKey("F9")
	if vk != 0x78 || mods&^uint32(MOD_NOREPEAT) != 0 {
		t.Errorf("F9: vk=0x%X mods=0x%X", vk, mods)
	}
	mods, vk = parseHotKey("Ctrl+Alt+T")
	if vk != 'T' || mods&MOD_CONTROL == 0 || mods&MOD_ALT == 0 || mods&MOD_SHIFT != 0 {
		t.Errorf("Ctrl+Alt+T: vk=0x%X mods=0x%X", vk, mods)
	}
	mods, vk = parseHotKey("shift+f12")
	if vk != 0x7B || mods&MOD_SHIFT == 0 {
		t.Errorf("shift+f12: vk=0x%X mods=0x%X", vk, mods)
	}
}
