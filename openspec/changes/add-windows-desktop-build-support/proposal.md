## Why

Desktop-дистрибутив сейчас собирается и публикуется только под macOS (см. `desktop-app-packaging`, `desktop-release-automation`) — Gradle-таск `desktopAppImage` умеет только `--type dmg`, а CI-релиз (`macos-release.yml`) публикует только два `.dmg`. Пользователям на Windows (целевые версии — 10 и 11; Windows 7 вне рамок — JDK 21/jpackage её официально не поддерживает) сейчас негде взять рабочий дистрибутив приложения.

## What Changes

- Gradle-таск упаковки (`backend/build.gradle`, `desktopAppImage`) расширяется веткой для Windows — собирает `.msi` через `jpackage --type msi`, по тому же принципу, что и текущая macOS-ветка (переиспользует `desktopAppLibs`/`desktopFxLibs`/`desktopFrontendAssets`/`desktopJlinkRuntime`, встроенный JRE, без внешних зависимостей на целевой машине).
- Повторная установка `.msi` более новой версии заменяет установленное приложение (не создаёт вторую копию) — через стабильный `--win-upgrade-uuid`, аналогично тому, как `--mac-package-identifier` обеспечивает это на macOS.
- На Windows jpackage-имя приложения — латинское `YaKonfigurator` (WiX/MSI не вмещает кириллицу в кодовую страницу 1252, JDK-8290471), поэтому файл называется `YaKonfigurator-<версия>-x86_64.msi`; на macOS имя `Я-Конфигуратор` не меняется.
- Требуется иконка приложения в формате `.ico` (сейчас есть только `.icns` для macOS) — конвертируется из существующего источника.
- Существующий workflow `.github/workflows/macos-release.yml` дополняется третьей веткой сборки — Windows x86_64 (раннер `windows-latest`, с предустановленным, но не добавленным в `PATH` WiX Toolset — требуется явно прописать `PATH` в workflow, иначе `jpackage --type msi` не найдёт `candle.exe`/`light.exe`), и публикует `.msi` как третий файл в том же релизе, рядом с двумя `.dmg`.
- Правило «нет частичного релиза» (см. `desktop-release-automation`, Публикация только после успеха обеих сборок) расширяется на три сборки — релиз публикуется только если все три (macOS arm64, macOS x86_64, Windows) прошли успешно.

## Capabilities

### New Capabilities

(нет)

### Modified Capabilities

- `desktop-app-packaging`: требование «Автономная установка без внешних зависимостей» расширяется на Windows-установщик (`.msi`); требование «Замена версии при повторной установке» обобщается с «нового `.dmg`» на любой из установщиков (`.dmg`/`.msi`).
- `desktop-release-automation`: добавляется требование сборки Windows-дистрибутива в CI; требования «Публикация только после успеха обеих сборок» и «Публикация в GitHub Releases с обоими дистрибутивами» обобщаются с двух сборок/файлов на три.

## Impact

- **Backend**: `backend/build.gradle` — ветка Windows в `desktopAppImage`; новый файл `backend/src/main/resources/desktop/app-icon.ico`.
- **CI**: `.github/workflows/macos-release.yml` — третья нога matrix (Windows, с собственным `appName: YaKonfigurator`), донастройка `PATH` под WiX, отдельный Windows-шаг сборки через `gradlew.bat`; имя workflow/файла можно оставить как есть или переименовать — решается в design.md.
- **Не затронуто**: сам backend/frontend код приложения, поведение на macOS (существующие ветки/таски не меняются, только добавляется новая), локальный скилл `macos-arm64-release`.
