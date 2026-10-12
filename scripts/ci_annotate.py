#!/usr/bin/env python3
"""Превращает лог Gradle в аннотации GitHub Actions.

Использование:  ci_annotate.py <build.log> [заголовок]

Зачем: полный лог сборки на GitHub большой, а причина падения обычно занимает
несколько строк. Скрипт вытаскивает ошибки компиляции Kotlin/Java, блоки
"What went wrong", упавшие тесты и публикует их аннотациями — они видны прямо
на странице запуска и доступны через API (check-runs/<id>/annotations).
"""
import pathlib
import re
import sys

MAX_ANNOTATIONS = 9
CHUNK = 14000


def esc(value: str) -> str:
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def collect(lines):
    picked = []
    seen = set()

    def add(block):
        text = "\n".join(block).rstrip()
        if text and text not in seen:
            seen.add(text)
            picked.append(text)

    i = 0
    n = len(lines)
    while i < n:
        line = lines[i]
        # Ошибки компилятора Kotlin / javac / KSP
        if line.startswith("e: ") or re.match(r"^.*\.java:\d+: error:", line) or line.startswith("error: "):
            add([line.replace("file:///home/runner/work/spoty/spoty/", "")])
        # Блок Gradle "What went wrong"
        elif line.startswith("* What went wrong:"):
            block = [line]
            i += 1
            while i < n and not lines[i].startswith("* Try:") and not lines[i].startswith("BUILD FAILED"):
                block.append(lines[i])
                i += 1
            add(block[:60])
            continue
        # Упавшие тесты
        elif re.search(r"\sFAILED$", line) and ("Test" in line or "test" in line):
            block = [line]
            j = i + 1
            while j < n and j < i + 8 and lines[j].startswith("    "):
                block.append(lines[j])
                j += 1
            add(block)
        elif "Could not resolve" in line or "Could not find" in line or "Unresolved reference" in line:
            add([line])
        i += 1
    return picked


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: ci_annotate.py <log> [title]")
        return 2
    path = pathlib.Path(sys.argv[1])
    title = sys.argv[2] if len(sys.argv) > 2 else "Build"
    if not path.exists():
        print(f"::error title={title}::лог {path} не найден")
        return 0
    lines = path.read_text(errors="replace").splitlines()
    picked = collect(lines)
    if not picked:
        picked = ["(Не найдено явных ошибок; последние строки лога)"] + lines[-120:]
    text = "\n".join(picked)
    chunks = [text[i:i + CHUNK] for i in range(0, len(text), CHUNK)]
    if len(chunks) > MAX_ANNOTATIONS:
        chunks = chunks[:MAX_ANNOTATIONS]
        chunks[-1] += "\n… (обрезано)"
    total = len(chunks)
    for idx, chunk in enumerate(chunks, 1):
        print(f"::error title={esc(title)} ({idx}/{total})::{esc(chunk)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
