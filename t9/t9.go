// Package main: ядро T9-предсказания (чистый Go, без Windows-зависимостей).
//
// Модель ввода: пользователь набирает первые буквы слова (в порядке
// следования), например «првт» вместо «привет». Движок подбирает слово
// из словаря, в котором набранные буквы идут по порядку с самого начала,
// и ранжирует кандидатов:
//  1. по количеству «пропущенных» букв (len(слово) - len(набрано)) —
//     чем меньше, тем ближе набранное слово к полному;
//  2. по позиции последней набранной буквы — чем раньше слово
//     «закрывается», тем лучше;
//  3. по частотности слова в корпусе.
package main

import (
	"embed"
	"strconv"
	"strings"
	"unicode"
)

//go:embed data/ru.txt data/en.txt
var dictFS embed.FS

// Language — язык предсказания.
type Language string

const (
	LanguageAuto Language = "auto"
	LanguageRU   Language = "ru"
	LanguageEN   Language = "en"
)

func languageFromString(s string) Language {
	switch Language(s) {
	case LanguageRU:
		return LanguageRU
	case LanguageEN:
		return LanguageEN
	default:
		return LanguageAuto
	}
}

type word struct {
	s     string
	freq  int32
	runes []rune
}

// dict — словарь, сгруппированный по первой букве для быстрой фильтрации.
type dict struct {
	words   []word
	byFirst map[rune][]int
}

// Engine — T9-движок со словарями для русского и английского.
type Engine struct {
	ru dict
	en dict
}

func newEngine() *Engine {
	return &Engine{
		ru: loadDict("data/ru.txt"),
		en: loadDict("data/en.txt"),
	}
}

func loadDict(name string) dict {
	b, err := dictFS.ReadFile(name)
	if err != nil {
		panic("t9: cannot read dictionary " + name + ": " + err.Error())
	}
	var d dict
	d.byFirst = make(map[rune][]int)
	for _, line := range strings.Split(string(b), "\n") {
		line = strings.TrimRight(line, "\r")
		sp := strings.LastIndexByte(line, ' ')
		if sp <= 0 {
			continue
		}
		w := line[:sp]
		c, err := strconv.Atoi(line[sp+1:])
		if err != nil || c <= 0 {
			continue
		}
		runes := []rune(w)
		if len(runes) < 2 {
			continue
		}
		i := len(d.words)
		d.words = append(d.words, word{s: w, freq: int32(c), runes: runes})
		d.byFirst[runes[0]] = append(d.byFirst[runes[0]], i)
	}
	return d
}

func (e *Engine) pick(lang Language, first rune) *dict {
	switch lang {
	case LanguageRU:
		return &e.ru
	case LanguageEN:
		return &e.en
	default:
		if isCyrillic(first) {
			return &e.ru
		}
		return &e.en
	}
}

func isCyrillic(r rune) bool {
	return (r >= 'а' && r <= 'я') || r == 'ё'
}

// Predict возвращает наиболее вероятное слово из словаря для набранной
// последовательности typed. Пустая строка — кандидатов нет.
func (e *Engine) Predict(lang Language, typed string) string {
	n := len([]rune(typed))
	if n < 2 || n > 32 {
		return ""
	}
	t := []rune(strings.ToLower(typed))
	d := e.pick(lang, t[0])

	bestI := -1
	bestDel, bestEnd := 0, 0
	var bestFreq int32

	for _, i := range d.byFirst[t[0]] {
		w := &d.words[i]
		wl := len(w.runes)
		if wl < n {
			continue
		}
		// w.runes[0] == t[0] гарантировано индексом byFirst.
		j := 1
		for k := 1; k < wl; k++ {
			if j < n && w.runes[k] == t[j] {
				j++
				if j == n {
					del := wl - n
					end := k + 1
					if bestI < 0 ||
						del < bestDel ||
						(del == bestDel && end < bestEnd) ||
						(del == bestDel && end == bestEnd && w.freq > bestFreq) {
						bestI = i
						bestDel = del
						bestEnd = end
						bestFreq = w.freq
					}
					break
				}
			}
		}
	}
	if bestI < 0 {
		return ""
	}
	return d.words[bestI].s
}

// ApplyCase переносит регистр набранного текста на предсказанное слово:
// «Првт»+«привет» → «Привет», «ПРВТ»+«привет» → «ПРИВЕТ».
func ApplyCase(typed, pred string) string {
	tr := []rune(typed)
	pr := []rune(pred)
	if len(pr) == 0 {
		return pred
	}
	upper := 0
	for _, r := range tr {
		if unicode.IsUpper(r) {
			upper++
		}
	}
	if upper == len(tr) && len(tr) >= 2 {
		return strings.ToUpper(string(pr))
	}
	if unicode.IsUpper(tr[0]) {
		pr[0] = unicode.ToUpper(pr[0])
	}
	return string(pr)
}
