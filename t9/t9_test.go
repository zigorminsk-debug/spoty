package main

import "testing"

func TestPredictRU(t *testing.T) {
	e := newEngine()
	cases := [][2]string{
		{"првт", "привет"},
		{"вчр", "вчера"},
		{"спс", "спас"},
		{"челвк", "человек"},
		{"здравствуйт", "здравствуйте"},
		{"пжст", "пожалуйста"},
		{"всгд", "всегда"},
		{"дктр", "доктор"},
		{"привет", "привет"}, // уже целое слово — не меняется
		{"работа", "работа"},
		{"на", "на"},
		{"п", ""}, // короче минимальной длины
		{"", ""},
	}
	for _, c := range cases {
		if got := e.Predict(LanguageAuto, c[0]); got != c[1] {
			t.Errorf("Predict(auto, %q) = %q, want %q", c[0], got, c[1])
		}
	}
}

func TestPredictEN(t *testing.T) {
	e := newEngine()
	cases := [][2]string{
		{"tht", "that"},
		{"wndw", "window"},
		{"wrk", "work"},
		{"wrkd", "worked"},
		{"hlp", "help"},
		{"abt", "about"},
		{"frm", "from"},
		{"nt", "not"},
		{"thnk", "think"},
		{"hello", "hello"},
		{"th", "th"}, // целое слово — не меняется
		{"windows", "windows"},
		{"a", ""},
		{"", ""},
	}
	for _, c := range cases {
		if got := e.Predict(LanguageAuto, c[0]); got != c[1] {
			t.Errorf("Predict(auto, %q) = %q, want %q", c[0], got, c[1])
		}
	}
}

func TestPredictLanguagePinning(t *testing.T) {
	e := newEngine()
	// Даже при закреплении языка кириллица ищется в своём словаре.
	if got := e.Predict(LanguageRU, "првт"); got != "привет" {
		t.Errorf("Predict(ru, првт) = %q, want привет", got)
	}
	if got := e.Predict(LanguageEN, "wrk"); got != "work" {
		t.Errorf("Predict(en, wrk) = %q, want work", got)
	}
	// В английском словаре кириллица не найдётся.
	if got := e.Predict(LanguageEN, "првт"); got != "" {
		t.Errorf("Predict(en, првт) = %q, want empty", got)
	}
	if got := e.Predict(LanguageRU, "wrk"); got != "" {
		t.Errorf("Predict(ru, wrk) = %q, want empty", got)
	}
}

func TestApplyCase(t *testing.T) {
	cases := [][3]string{
		{"првт", "привет", "привет"},
		{"Првт", "привет", "Привет"},
		{"ПРВТ", "привет", "ПРИВЕТ"},
		{"wrk", "work", "work"},
		{"Wrk", "work", "Work"},
	}
	for _, c := range cases {
		if got := ApplyCase(c[0], c[1]); got != c[2] {
			t.Errorf("ApplyCase(%q, %q) = %q, want %q", c[0], c[1], got, c[2])
		}
	}
}

func TestEngineSanity(t *testing.T) {
	e := newEngine()
	if len(e.ru.words) < 10000 || len(e.en.words) < 10000 {
		t.Fatalf("dictionaries too small: ru=%d en=%d", len(e.ru.words), len(e.en.words))
	}
	// Словарь должен быть отсортирован по убыванию частотности
	// (порядок файла — частотный).
	for i := 1; i < len(e.ru.words); i++ {
		if e.ru.words[i].freq > e.ru.words[i-1].freq {
			t.Fatalf("ru dict not frequency-sorted at %d", i)
		}
	}
}
