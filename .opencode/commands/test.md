---
description: Полный прогон тестов: DSP, lint, при необходимости — Compose UI
agent: qa-engineer
subagent: false
---

Проверь проект:

1. `.\gradlew.bat :core:model:test :core:dsp:test :app:assembleDebug`
2. `.\gradlew.bat :app:lintDebug`
3. Если указано «на устройстве» или подключён эмулятор/телефон:
   `.\gradlew.bat :app:connectedDebugAndroidTest`.

Аргументы: $ARGUMENTS

В отчёте: что запускалось, что упало (файл:строка), какие тесты не выполнялись и почему.
Не скрывай пропуски.
