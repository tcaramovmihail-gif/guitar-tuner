---
description: Запустить приложение на эмуляторе или телефоне и снять скриншот
agent: qa-engineer
subagent: false
---

Проверь устройство и запусти приложение.

1. `adb devices -l`. Если пусто — предложи `.\scripts\emulator.ps1 start` или подключить
   телефон по USB/Wi-Fi.
2. `.\scripts\emulator.ps1 status` (если эмулятор).
3. `.\scripts\device.ps1 install`
4. `.\scripts\device.ps1 run`
5. `.\scripts\device.ps1 screenshot`
6. Проверь logcat на `FATAL EXCEPTION`.

Аргументы: $ARGUMENTS

В ответе: серийник устройства, что открылось, путь к скриншоту, ошибки.
Если эмулятор без гипервизора — сообщи, что нужно установить AEHD/WHPX, не пытайся
подменять результат.
