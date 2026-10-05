## 1. Иконка и подготовка

- [x] 1.1 Сконвертировать `docs/fc-icon.png` в многоразмерную `.ico` и сохранить как `backend/src/main/resources/desktop/app-icon.ico`
- [x] 1.2 Сгенерировать один раз стабильный GUID для `--win-upgrade-uuid` (например, `python3 -c "import uuid; print(uuid.uuid4())"`) — зафиксировать значение, использовать в задаче 2.1 — сгенерирован `426abc10-ac1d-454f-b9fc-7b344800e814`

## 2. Windows-ветка в Gradle

- [x] 2.1 В `desktopAppImage` (`backend/build.gradle`) добавить ветвление по текущей ОС (например, через `org.gradle.internal.os.OperatingSystem.current().isWindows()`): для Windows — `--type msi`, `--icon app-icon.ico`, `--win-upgrade-uuid <зафиксированный GUID из 1.2>`, `--win-menu`, `--win-shortcut`, вместо macOS-флагов (`--type dmg`, `--mac-package-identifier`, `.icns`); переиспользуемые части (`--input`, `--main-jar`, `--main-class`, `--runtime-image`, `--app-content`, `--dest`) остаются общими
- [x] 2.2 Переименование итогового файла (`doLast` в `desktopAppImage`) сделать зависимым от типа пакета (`.dmg` на macOS, `.msi` на Windows) вместо жёстко зашитого `.dmg`
- [x] 2.3 Убедиться, что `desktopVerifyArch` не пытается выполняться на Windows (там нет `hdiutil`/`file`) — сделать его условным по ОС (no-op вне macOS) либо завести отдельную, более простую проверку архитектуры под Windows на усмотрение реализации

## 3. Локальная проверка на Windows

Пропущено по решению пользователя — нет физической Windows-машины под рукой; риск (раскладка `$APPDIR/../fxlibs` на Windows не проверена заранее) принят осознанно, см. design.md, Risks. Первая проверка выполняется прямо в CI (группа 5) на раннере `windows-latest`.

- [ ] 3.1 Установить WiX Toolset v3 на тестовой Windows-машине (`choco install wixtoolset`), если ещё не установлен
- [ ] 3.2 Собрать `./gradlew desktopAppImage -PappVersion=<тестовая_версия>` на Windows-машине, убедиться, что `.msi` собирается без ошибок
- [ ] 3.3 Установить собранный `.msi`, проверить, что приложение запускается, открывается в отдельном окне (не в браузере), работает без отдельно установленной Java/БД
- [ ] 3.4 Проверить раскладку `--app-content` относительно `$APPDIR` на Windows (см. design.md, Decisions) — если `$APPDIR/../fxlibs` не работает (JavaFX WebView не находит модули), поправить путь в `--java-options` для Windows-ветки прежде, чем переходить к группе 4
- [ ] 3.5 Установить поверх предыдущей установки более новую версию `.msi`, убедиться, что старая копия заменяется, а не дублируется (проверка `--win-upgrade-uuid`)

## 4. CI: третья нога в существующем workflow

- [x] 4.1 В `.github/workflows/macos-release.yml` добавить третий элемент `matrix.include` — `os: windows-latest`, с явным полем `artifactName` (не производным от `arch`, см. design.md, Decisions) для всех трёх элементов matrix (`release-macos-aarch64`, `release-macos-x86_64`, `release-windows-x86_64`)
- [x] 4.2 В Windows-ноге — шаг, добавляющий bin-директорию WiX Toolset в `$GITHUB_PATH` (поиск через glob `C:\Program Files (x86)\WiX Toolset v3*\bin`, не хардкодить минорную версию)
- [x] 4.3 Обновить шаг `actions/upload-artifact` — использовать `matrix.artifactName` вместо `dmg-${{ matrix.arch }}`; путь к файлу — per-OS (`.msi` для Windows-ноги)
- [x] 4.4 Обновить шаг `actions/download-artifact` в `publish` — паттерн `release-*` вместо `dmg-*`
- [x] 4.5 Убедиться, что `gh release create` в `publish` подхватывает все три файла из `dist/` (glob `dist/*` вместо `dist/*.dmg`, чтобы захватить и `.msi`)

## 5. Проверка

- [x] 5.1 `tsc -b`/`oxlint` не требуются (изменения только в `build.gradle`/workflow) — убедиться, что `./gradlew desktopAppImage` по-прежнему собирается на macOS без регрессий (обе архитектуры) — прогнано локально на x86_64, `desktopVerifyArch` подтвердил архитектуру; arm64-машины под рукой нет, за вторую архитектуру отвечает CI-прогон (задача 5.2)
- [ ] 5.2 Прогнать workflow вручную на реальной/тестовой версии, убедиться, что все три job'а `build` (macos-14, macos-15-intel, windows-latest) завершаются успешно и `publish` публикует релиз с тремя файлами — прогоны выполнялись (30.09–01.10.2026), по их итогам сделаны исправления группы 6; финальный успешный прогон всех трёх job'ов и публикация релиза не подтверждены
- [ ] 5.3 Скачать `.msi` с опубликованного релиза на чистую Windows 10/11 машину, установить, убедиться, что приложение запускается
- [ ] 5.4 Проверить сценарий «Windows падает» — временно сломать Windows-сборку, убедиться, что релиз не публикуется вовсе (ни один из трёх файлов), несмотря на то что обе macOS-сборки прошли успешно

## 6. Исправления по итогам первых CI-прогонов

Внесены в код отдельными коммитами после основной реализации (группы 2 и 4); отражены здесь, чтобы артефакты соответствовали коду.

- [x] 6.1 `desktopFrontendBuild`: вызывать `npm.cmd` вместо `npm` на Windows (`Exec` не идёт через shell) — b92e4af
- [x] 6.2 `jlink --module-path`: использовать `File.pathSeparator` вместо хардкода `:` — 2406370
- [x] 6.3 Передавать аргументы jpackage на Windows через `@argfile` (UTF-8), в обход ANSI-кодирования argv в `ProcessBuilder` — 57b9387
- [x] 6.4 Workflow: `"-PappVersion=..."` в кавычках в отдельном Windows-шаге сборки (`gradlew.bat`) — 50ac790
- [x] 6.5 Латинское имя `YaKonfigurator` для jpackage на Windows (`jpackageName` в `build.gradle`, `matrix.appName` в workflow), macOS без изменений — 3713b62
