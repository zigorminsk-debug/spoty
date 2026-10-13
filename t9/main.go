//go:build !windows

package main

import "fmt"

// Спотовый T9 — приложение для Windows. Собирается так:
//
//	GOOS=windows GOARCH=amd64 go build -ldflags "-s -w -H windowsgui" -o spoty-t9.exe .
func main() {
	fmt.Println("Spoty T9 работает только под Windows. Сборка: см. t9/README.md")
}
