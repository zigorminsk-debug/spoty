//go:build windows

package main

import (
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
)

var (
	logMu  sync.Mutex
	logF   *os.File
	logDir string
)

func initLog() {
	if os.Getenv("LOCALAPPDATA") == "" {
		return
	}
	d := logDir
	if err := os.MkdirAll(d, 0o755); err != nil {
		return
	}
	f, err := os.OpenFile(filepath.Join(d, "spoty-t9.log"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		return
	}
	// Простейшая ротация: больше 1 МБ — в сторону.
	if fi, err := f.Stat(); err == nil && fi.Size() > 1<<20 {
		f.Close()
		_ = os.Rename(filepath.Join(d, "spoty-t9.log"), filepath.Join(d, "spoty-t9.log.old"))
		if f, err = os.OpenFile(filepath.Join(d, "spoty-t9.log"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644); err != nil {
			return
		}
	}
	logF = f
}

func logf(format string, args ...any) {
	logMu.Lock()
	defer logMu.Unlock()
	if logF == nil {
		return
	}
	line := fmt.Sprintf("%s %s\n", time.Now().Format("2006-01-02 15:04:05"), fmt.Sprintf(format, args...))
	_, _ = logF.WriteString(line)
}

func logPath() string {
	return filepath.Join(logDir, "spoty-t9.log")
}

func closeLog() {
	logMu.Lock()
	defer logMu.Unlock()
	if logF != nil {
		logF.Close()
		logF = nil
	}
}
