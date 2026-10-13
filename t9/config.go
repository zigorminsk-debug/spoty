//go:build windows

package main

import (
	"encoding/json"
	"os"
	"path/filepath"
)

// Config — пользовательские настройки (APPDATA\SpotyT9\config.json).
type Config struct {
	HotKey   string `json:"hotKey"`   // горячая клавиша: "F9", "Ctrl+Alt+T", ...
	Language string `json:"language"` // "auto" | "ru" | "en"
	GapMs    int    `json:"gapMs"`    // пауза (мс) для автозавершения слова
	MinLen   int    `json:"minLen"`   // минимальная длина набираемого слова
}

func defaultConfig() Config {
	return Config{HotKey: "F9", Language: "auto", GapMs: 1000, MinLen: 2}
}

func configDir() string {
	return filepath.Join(os.Getenv("APPDATA"), "SpotyT9")
}

func configPath() string {
	return filepath.Join(configDir(), "config.json")
}

func loadConfig() Config {
	c := defaultConfig()
	b, err := os.ReadFile(configPath())
	if err != nil {
		return c
	}
	if err := json.Unmarshal(b, &c); err != nil {
		logf("config: не удалось разобрать: %v", err)
		return defaultConfig()
	}
	d := defaultConfig()
	if c.HotKey == "" {
		c.HotKey = d.HotKey
	}
	switch c.Language {
	case "auto", "ru", "en":
	default:
		c.Language = d.Language
	}
	if c.GapMs < 200 {
		c.GapMs = d.GapMs
	}
	if c.MinLen < 2 {
		c.MinLen = d.MinLen
	}
	if c.MinLen > 8 {
		c.MinLen = 8
	}
	return c
}

func saveConfig(c Config) {
	if err := os.MkdirAll(configDir(), 0o755); err != nil {
		return
	}
	b, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return
	}
	_ = os.WriteFile(configPath(), b, 0o644)
}
