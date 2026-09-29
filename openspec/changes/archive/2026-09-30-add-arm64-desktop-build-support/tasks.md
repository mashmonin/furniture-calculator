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

## 4. Фиксация вендора JDK для тулчейна

- [x] 4.1 `backend/settings.gradle` — подключён плагин `org.gradle.toolchains.foojay-resolver-convention`; `backend/build.gradle` — `java.toolchain` получил `vendor = JvmVendorSpec.ADOPTIUM`. Причина: без явного vendor Gradle резолвит `toolchain{}` в любой подходящий JDK 21, уже стоящий на машине сборки — включая `brew install openjdk@21` (см. старый текст задачи 4.1 ниже). На Apple Silicon Mac, где JDK был поставлен именно через Homebrew, это привело к тому, что `desktopJlinkRuntime` встроил в рантайм `libfontmanager.dylib` с жёстко прошитым путём `/usr/local/opt/harfbuzz/...` — на машине без этого конкретного Homebrew-пакета экспорт в Excel (Apache POI → AWT/Font) падал в рантайме с `HTTP 500` / `UnsatisfiedLinkError`. Проверено на Intel-машине: `./gradlew javaToolchains` теперь резолвится в `Eclipse Temurin JDK 21`, а не в параллельно установленный `Homebrew JDK 21`.
- [x] 4.2 Проверено по репозиторию (`docs/`, `.claude/skills/`): установка JDK через `brew install openjdk@21` нигде больше не упоминалась — обновлять, кроме этого файла, нечего.

## 5. Проверка на реальном Apple Silicon Mac

- [x] 5.1 На Apple Silicon Mac поставить Xcode Command Line Tools (`xcode-select --install`), Node.js — JDK 21 (Temurin, arm64) ставить вручную/через Homebrew больше не нужно: `./gradlew` скачает его сам через `foojay-resolver-convention` (см. задачу 4.1). Отмечено выполненным по подтверждению пользователя — самостоятельно прогнал на реальном Apple Silicon Mac, автоматизированная проверка в этой (x86_64) среде невозможна.
- [x] 5.2 Собрать `./gradlew desktopAppImage` — убедиться, что имя `.dmg` содержит `aarch64`, а verification-таск (задача 3) подтверждает нативную arm64-сборку. Отмечено выполненным по подтверждению пользователя.
- [x] 5.3 Установить и запустить собранный `.app` — повторить проверки из задачи 4.4 change `add-desktop-app-packaging` (полный Liquibase-прогон на чистой БД, `GET /`, `GET /api/door-configurations` — 200, окно открывается без Rosetta); отдельно проверить «Скачать excel спецификацию» — именно этот сценарий падал из-за бага, описанного в задаче 4.1. Отмечено выполненным по подтверждению пользователя.
- [x] 5.4 Обновить design.md/tasks.md этого изменения результатами (что реально собралось и проверилось на arm64) перед архивацией — задачи 5.1–5.3 обновлены отметкой о подтверждении пользователем на реальном железе; отдельных числовых результатов сборки (версия, хэш dmg) пользователь не предоставил.
