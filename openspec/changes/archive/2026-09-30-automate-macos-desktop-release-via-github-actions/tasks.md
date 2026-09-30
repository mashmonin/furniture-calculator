## 1. Workflow: каркас и вход

- [x] 1.1 Создать `.github/workflows/macos-release.yml` с триггером `workflow_dispatch` и обязательным текстовым input `version` (semver, например `1.2.0`)
- [x] 1.2 Job `check-version` (`ubuntu-latest`): простая проверка формата `version` регуляркой (`X.Y.Z`) — падать с понятной ошибкой, если формат не похож на semver
- [x] 1.3 В том же job — `gh release view "v${VERSION}"`; если команда завершается успешно (релиз уже существует), явно упасть с ошибкой до сборки (см. design.md, Decisions)

## 2. Workflow: сборка под обе архитектуры

- [x] 2.1 Job `build` с matrix (`macos-14`/arm64, `macos-15-intel`/x86_64 — `macos-13` оказался выведен из эксплуатации 4 декабря 2025, job с ним никогда не получает раннер; заменено по факту обнаружения при первом реальном прогоне), `needs: check-version`
- [x] 2.2 Шаги: checkout, установка JDK 21 (Temurin, см. `backend/build.gradle` — `vendor = JvmVendorSpec.ADOPTIUM`) через `actions/setup-java`, установка Node (для `desktopFrontendBuild`) через `actions/setup-node`
- [x] 2.3 Запуск `./gradlew desktopAppImage -PappVersion=${{ inputs.version }}` из `backend/`
- [x] 2.4 Определить итоговый путь `.dmg` по архитектуре (`backend/build/desktop/<aarch64|x86_64>/dmg/Я-Конфигуратор-<version>-<arch>.dmg`) и залить его через `actions/upload-artifact` под именем, включающим архитектуру (например, `dmg-aarch64`/`dmg-x86_64`)

## 3. Workflow: публикация релиза

- [x] 3.1 Job `publish` (`ubuntu-latest`), `needs: build` — не запускается, если сборка любой архитектуры упала (см. design.md, Decisions)
- [x] 3.2 Скачать оба артефакта (`actions/download-artifact`)
- [x] 3.3 `gh release create "v${VERSION}" <оба .dmg-файла> --title "v${VERSION}"` — публикация релиза с обоими файлами как assets

## 4. Проверка

- [x] 4.1 Прогнать workflow вручную на тестовой версии (например, `0.0.1-ci-test` или следующей реальной версии — по решению пользователя) и убедиться, что релиз опубликован с двумя файлами, различимыми по архитектуре в имени — прогнано на реальной версии `5.0.0`, релиз опубликован успешно (run #4)
- [x] 4.2 Проверить сценарий отказа: запустить повторно с той же версией — workflow должен остановиться на `check-version`, не запуская сборку и не трогая существующий релиз
- [x] 4.3 Проверить сценарий частичного провала (например, временно сломать сборку одной архитектуры) — релиз не должен публиковаться вовсе
- [x] 4.4 Скачать оба `.dmg` с опубликованного релиза и установить на соответствующих Mac (Apple Silicon и Intel), убедиться, что приложение запускается
