---
description: Подготовить релизный артефакт и чек-лист RuStore
agent: release-manager
subagent: false
---

Подготовь релиз, но не публикуй его.

1. Проверь `versionCode`, `versionName`, `applicationId` в `app/build.gradle.kts`.
2. Убедись, что ключи подписи и `keystore.properties` не в git.
3. Собери `.\gradlew.bat :app:bundleRelease` (или `assembleRelease`).
4. Проверь подпись.
5. Прогони тесты и lint.
6. Составь чек-лист RuStore по `docs/PLAN.md`.

Аргументы: $ARGUMENTS

В ответе: путь к AAB/APK, SHA-256, версия, что осталось заполнить в консоли RuStore.
Не загружай приложение и не создавай ключ без явного подтверждения.
