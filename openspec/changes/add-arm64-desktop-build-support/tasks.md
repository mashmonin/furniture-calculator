## 1. Определение архитектуры и раскладка выходных путей

- [x] 1.1 `backend/build.gradle` — `desktopArch` вычисляется из `System.getProperty("os.arch")` (нормализация `amd64`/`x86_64` → `x86_64`, `aarch64`/`arm64` → `aarch64`), тот же сигнал, что использует `org.openjfx.javafxplugin`.
- [x] 1.2 Выходные директории (`desktopAppLibs`, `desktopFxLibs`, `desktopJlinkRuntime`, `desktopAppImage`) перенесены под `build/desktop/${desktopArch}/...`.
- [x] 1.3 Проверено на Intel-машине: `./gradlew desktopVerifyArch` (тянет весь пайплайн, включая `desktopAppImage`) — `BUILD SUCCESSFUL`, всё разложилось под `build/desktop/x86_64/...`.

## 2. Именование итогового `.dmg`

- [x] 2.1 Итоговый файл переименовывается в `doLast` после `jpackage` (который сам называет его `<name>-<version>.dmg`) в `<name>-<version>-<arch>.dmg` — подтверждено реальной сборкой: `Я-Конфигуратор-1.0.1-x86_64.dmg`. `--name`/`CFBundleName` не меняли.
- [x] 2.2 `grep` по репозиторию (`*.md`, `*.gradle`, `*.yml`) на старое имя без архитектуры — ничего не нашлось, обновлять нечего.

## 3. Автоматическая проверка архитектуры результата

- [x] 3.1 Таск `desktopVerifyArch` — монтирует собранный `.dmg` (`hdiutil attach -readonly`), запускает `file` на `Contents/MacOS/<AppName>`, сверяет с ожидаемым маркером (`arm64`/`x86_64`), размонтирует. Реализовано через обычный Groovy `List.execute()`, а не `project.exec {}` — тот в этой версии Gradle (9.5.1) оказался недоступен как closure-based API прямо в теле таска (`Could not find method exec()`).
- [x] 3.2 `desktopAppImage.finalizedBy(desktopVerifyArch)` — запуск `./gradlew desktopAppImage` теперь всегда включает проверку; `desktopVerifyArch.dependsOn(desktopAppImage)` — так же работает как отдельная цель.
- [x] 3.3 Проверено на Intel-машине: `Архитектура подтверждена (x86_64): .../Contents/MacOS/Я-Конфигуратор: Mach-O 64-bit executable x86_64`.

## 4. Проверка на реальном Apple Silicon Mac

- [ ] 4.1 На Apple Silicon Mac поставить JDK 21 (arm64, `brew install openjdk@21`), Xcode Command Line Tools (`xcode-select --install`), Node.js
- [ ] 4.2 Собрать `./gradlew desktopAppImage` — убедиться, что имя `.dmg` содержит `aarch64`, а verification-таск (задача 3) подтверждает нативную arm64-сборку
- [ ] 4.3 Установить и запустить собранный `.app` — повторить проверки из задачи 4.4 change `add-desktop-app-packaging` (полный Liquibase-прогон на чистой БД, `GET /`, `GET /api/door-configurations` — 200, окно открывается без Rosetta)
- [ ] 4.4 Обновить design.md/tasks.md этого изменения результатами (что реально собралось и проверилось на arm64) перед архивацией
