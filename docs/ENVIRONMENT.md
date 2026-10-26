# Среда разработки и тестирования

Всё, что нужно для сборки, тестов и запуска на эмуляторе/телефоне. Бинарники SDK
лежат вне git (`D:\Android`), в репозитории — скрипты и конфигурация.

## Что установлено

| Компонент | Версия / путь |
| --- | --- |
| JDK | Zulu 17.0.15, `C:\Program Files\Zulu\zulu-17` |
| Android SDK | `D:\Android\sdk` (cmdline-tools 16111833) |
| Android CLI | `D:\Android\sdk\cmdline-tools\latest\bin\android.exe` 1.0.16625370 |
| Platform | Android 36 |
| Build Tools | 36.0.0 |
| Platform Tools / ADB | 37.0.1 |
| Emulator | установлен, образ `system-images;android-36;google_apis;x86_64` |
| Gradle | 8.13, `D:\Android\gradle\gradle-8.13` + wrapper в проекте |
| Node.js | 24.16.0 (для опционального mobile-mcp) |

Переменные пользователя: `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `JAVA_HOME`, `PATH`
(platform-tools, cmdline-tools, emulator, gradle).

## Быстрый старт

```powershell
cd D:\opencode\guitar_tuner_ac
. .\scripts\env.ps1                 # переменные окружения в текущей сессии
.\gradlew.bat :app:assembleDebug    # debug APK
.\gradlew.bat :core:dsp:test        # DSP-тесты на JVM
.\scripts\emulator.ps1 start        # запустить AVD
.\scripts\device.ps1 install        # поставить APK на устройство/эмулятор
.\scripts\device.ps1 screenshot     # снимок экрана в artifacts/screens
```

APK: `app\build\outputs\apk\debug\app-debug.apk`.

## Эмулятор

Создаётся один раз:

```powershell
.\scripts\emulator.ps1 create     # avdmanager + образ android-36 google_apis x86_64
.\scripts\emulator.ps1 start
.\scripts\emulator.ps1 status
.\scripts\emulator.ps1 stop
```

Если эмулятор не стартует из-за гипервизора:

- быстрый путь без перезагрузки: пакет AEHD уже скачан,
  запустить от администратора
  `D:\Android\sdk\extras\google\Android_Emulator_Hypervisor_Driver\silent_install.bat`;
- либо включить Windows Hypervisor Platform (WHPX) и перезагрузиться;
- AEHD и Hyper-V/WHPX одновременно не используются: AEHD нужен, когда Hyper-V выключен.

### Ограничение текущей машины (проверено 24.10.2026)

Хост сам работает под гипервизором, и гостю не проброшены EPT и unrestricted guest.
Вывод `Coreinfo64.exe -v`:

```text
HYPERVISOR  -  Hypervisor is present
VMX         *  Supports Intel hardware-assisted virtualization
EPT         -  Supports Intel extended page tables (SLAT)
URG         -  Supports Intel unrestricted guest
```

AEHD 2.2 устанавливается и отвечает `emulator -accel-check` →
`0 / AEHD 2.2 installed and usable`, но QEMU зависает до старта гостевого ядра:
`adb` показывает `emulator-5554 offline`, процесс `qemu-system-x86_64` набирает
меньше секунды CPU и дальше не растёт. То же самое с `-accel off` (TCG),
`-gpu off/swiftshader_indirect/auto`, в headless и в оконном режиме, после
`-wipe-data` и пересоздания AVD. Ядро гостя не выводит ни строки.

Вывод: без вложенной виртуализации с EPT/URG эмулятор на этой машине не
запускается; WHPX требует те же возможности. Для UI-проверок использовать
реальный телефон (раздел ниже) либо машину без гипервизора / с проброшенными
EPT+URG. Точность тюнера на эмуляторе не проверяется в любом случае.

**Важно:** микрофон эмулятора пробрасывает звук с хоста с задержкой и обрабатывает его.
Для проверки точности тюнера он не годится — только для UI и дымовых тестов.

## Реальный телефон

1. Включить «Для разработчиков» → «Отладка по USB».
2. Подключить кабель, подтвердить отпечаток ключа.
3. `adb devices` — устройство должно быть в состоянии `device`.

По Wi-Fi (Android 11+): «Отладка по Wi-Fi» → «Сопряжение с кодом»:

```powershell
adb pair <ip:port> <код>
adb connect <ip:порт>
```

Установка и запуск:

```powershell
.\scripts\device.ps1 install
.\scripts\device.ps1 run
.\scripts\device.ps1 logs        # logcat в реальном времени
```

## Android CLI (рекомендуется агенту)

Официальный CLI заменил часть `sdkmanager` и умеет работать с устройством:

```powershell
android info
android sdk list
android emulator list
android run --apks app\build\outputs\apk\debug\app-debug.apk
android screen capture -o artifacts\screen.png
android layout                      # дерево UI
android input tap 540 1200
android docs search "audio latency"
```

`android skills list` — официальные skill-файлы для агентов (android-cli, agp-9-upgrade,
adaptive, profiler и др.). При необходимости: `android skills add android-cli`.

## Тесты

```powershell
.\gradlew.bat :core:dsp:test                  # JVM, синтетические струны
.\gradlew.bat :app:lintDebug                  # Android Lint
.\gradlew.bat :app:connectedDebugAndroidTest  # Compose UI на эмуляторе/телефоне
```

`connectedDebugAndroidTest` переустанавливает приложение и может стереть его данные
(импортированные песни). На рабочем телефоне безопаснее поставить APK с `-r` и
запустить тесты вручную:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -e class ru.eurovafel.guitartuner.MainActivityTest `
  ru.eurovafel.guitartuner.test/androidx.test.runner.AndroidJUnitRunner
```

Проверка DSP не требует устройства: тест генерирует щипковую струну с гармониками,
шумом и расстройкой, детектор обязан попасть в ±3 цента.

## Подпись и RuStore

- `keystore.properties` и `*.jks` — только локально, в git не попадают.
- Release-сборка: `.\gradlew.bat :app:bundleRelease` (AAB) или `assembleRelease` (APK).
- Для RuStore нужен подписанный файл, уникальный `applicationId`, растущий
  `versionCode`, иконка 512×512, ≥3 скриншота, политика конфиденциальности
  (доступ к микрофону).
- Ключ подписи создаётся один раз и хранится с резервной копией: смена ключа ломает
  обновления.

## Диагностика

| Симптом | Что делать |
| --- | --- |
| `JAVA_HOME` не задан | `. .\scripts\env.ps1` или перезапустить терминал |
| `SDK location not found` | проверить `local.properties` и `ANDROID_HOME` |
| `adb` не видит телефон | драйвер, режим передачи файлов, подтвердить отладку |
| Эмулятор: «Hypervisor not installed» | WHPX/AEHD, см. выше |
| Эмулятор: `offline`, QEMU не грузит CPU | хост под гипервизором без EPT/URG — см. «Ограничение текущей машины», тестировать на телефоне |
| `.ps1` падает с `UnexpectedToken` после кириллицы | сохранить скрипт как ASCII или UTF-8 с BOM: PowerShell 5.1 читает UTF-8 без BOM как ANSI |
| Крабозябры в консоли | `[Console]::OutputEncoding = [Text.Encoding]::UTF8` |
| Долгая первая сборка | скачивание зависимостей Gradle/Maven, это нормально |
| `npm.ps1` заблокирован политикой | использовать `npm.cmd` |

## Воспроизведение на другой машине

```powershell
.\scripts\bootstrap.ps1 -SdkPath D:\Android\sdk
```

Скрипт ставит cmdline-tools, platform-tools, platform 36, build-tools, emulator и
системный образ, создаёт AVD. JDK 17 и Node.js ставятся отдельно (или через winget).
