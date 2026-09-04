## 1. Пробная сборка (spike)

- [x] 1.1 Прогнать полную цепочку Liquibase changeset'ов из `backend/src/main/resources/db/changelog/` против H2 в режиме `MODE=PostgreSQL`, зафиксировать все changeset'ы, которые падают или ведут себя иначе, чем на PostgreSQL
- [x] 1.2 Собрать урезанный JRE через `jlink` под текущий набор модулей backend (без JavaFX) и замерить фактический размер
- [x] 1.3 Собрать пробный `.dmg` через `jpackage` с текущим backend (fat-jar) и H2 вместо PostgreSQL, без JavaFX-окна (запуск через открытие `localhost` в системном браузере) — замерить итоговый размер дистрибутива и потребление RAM при работе
- [x] 1.4 Добавить JavaFX (`javafx.controls`, `javafx.graphics`, `javafx.web`) в пробную сборку, открыть текущий фронтенд в `WebView` — подтверждено (`LOAD-STATE SUCCEEDED` в логах спайка), в процессе найден и исправлен блокирующий баг с зарезервированным словом `value` в H2 (см. design.md). Визуальная проверка отрисовки Ant Design в WebKit **не завершена** — открытый риск, см. design.md, Risks.
- [x] 1.5 Результаты сведены в design.md (Context, Decisions, Risks). Корректировок общего подхода не потребовалось; добавлена новая находка про `value` и открытый риск по визуальной проверке WebView.

## 2. Профиль desktop и база данных

- [x] 2.1 Добавлен Spring-профиль `desktop` — `backend/src/main/resources/application-desktop.yml` (datasource на файловый H2, `MODE=PostgreSQL;NON_KEYWORDS=VALUE`, `spring.liquibase.contexts: desktop`) и зависимость `com.h2database:h2` в `backend/build.gradle` (уже была добавлена ранее).
- [x] 2.2 Путь к файлу БД — `${user.home}/Library/Application Support/FurnitureCalculator/data/furniture-calculator` (вне `.app`-бандла, задаётся прямо в JDBC URL профиля `desktop`). Отдельного кода для создания каталога не потребовалось: H2 сам создаёт все недостающие родительские директории при первом подключении к файловой БД (проверено, включая путь с пробелом — `Application Support`). Проверено сквозным тестом `backend/src/test/java/com/example/furniturecalculator/config/DesktopProfileTest.java` (реальная активация профиля `desktop` через `@ActiveProfiles`, `user.home` подменён на временную директорию) — полный Liquibase-прогон (226/28) и repository-запрос отрабатывают штатно.
- [x] 2.3 Исправить changeset'ы, помеченные как несовместимые с H2 по итогам пункта 1.1 — реализовано **без правки уже применённых changeset'ов**: новая директория `changes-desktop-overrides/` (копии 0006/0008/0054 с индексами через generated-колонки под H2), переключение через Liquibase `context` на уровне `include` в `db.changelog-master.yaml` + `spring.liquibase.contexts: "!desktop"` в `application.yml`. Подход отличается от изначально описанного в design.md (единый changelog без ветвления) — Liquibase не позволял это сделать при ограничении "не трогать применённые changeset'ы" (останавливает всю цепочку на первом упавшем changeset'е, а проблемные — один из первых в цепочке). Отдельно решена проблема зарезервированного в H2 слова `value` через `NON_KEYWORDS=VALUE` на JDBC URL (не через JPA-квотирование, как считалось на шаге 1.4, — не работало из-за разного регистра квотирования между H2 и PostgreSQL). См. design.md, Decisions.
- [x] 2.4 Убедиться, что полный набор Liquibase-миграций проходит на чистой H2-БД в профиле `desktop` — подтверждено сквозным тестом `DesktopProfileTest` (см. 2.2) и ранее изолированно спайк-тестом на in-memory H2: 226 changeset'ов выполнено, 28 отфильтровано по context, плюс smoke-запросы через реальные repository-методы (DoorConfiguration, LinerDimensionOption, ColourOption, ConfigurationPrice, DimensionSurchargeRule) — без ошибок. Дополнительно перепроверено на реальном локальном PostgreSQL (`./gradlew bootRun`) — 0 новых changeset'ов, приложение стартует штатно, существующая БД не пострадала.

## 3. Обёртка JavaFX WebView

- [x] 3.1 Добавлены JavaFX-зависимости в `backend/build.gradle` (плагин `org.openjfx.javafxplugin`, модули `javafx.controls`, `javafx.web`) в том же модуле `backend` — отдельный Gradle-модуль не потребовался. Плагин `application` + `mainClass` добавлены для удобного `./gradlew run` (обычный сервер по-прежнему через `bootRun`).
- [x] 3.2 `backend/src/main/java/com/example/furniturecalculator/desktop/DesktopLauncher.java` — поднимает Spring Boot backend в фоновом потоке (`SpringApplicationBuilder`, профиль `desktop`, `--server.port=0` как аргумент командной строки — через `.properties(...)` не сработало бы, там более низкий приоритет, чем у `application.yml`) и после старта открывает `WebView` на `http://localhost:<реальный порт>/` (порт берётся из `WebServerApplicationContext`). Проверено тестом `DesktopLauncherBackendStartupTest` (тот же способ запуска, без самого окна) и реальным `./gradlew run`.
- [x] 3.3 Жизненный цикл: JavaFX по умолчанию вызывает `Application.stop()` при закрытии последнего окна — переопределён так, что закрывает Spring-контекст (останавливает backend) и завершает процесс (`System.exit(0)`).
- [x] 3.4 Заголовок окна — "Furniture Calculator". Иконка — предоставлена пользователем (файл из другого его проекта, `doctor-appointment/frontend/public/images/logopic.png`, скопирован в `backend/src/main/resources/desktop/app-icon.png`), подключена через `Stage.getIcons()`. Проверено тестом `DesktopLauncherIconTest` (загрузка ресурса как JavaFX `Image`, без ошибок, корректные размеры).

## 4. Упаковка и идентификация версий

- [ ] 4.1 Настроить Gradle-таск для `jlink` (кастомный JRE под нужные модули, включая JavaFX)
- [ ] 4.2 Настроить Gradle-таск для `jpackage`, собирающий `.dmg` с постоянным `--mac-package-identifier` между сборками
- [ ] 4.3 Прокинуть версию из Gradle-проекта в `CFBundleVersion` собираемого `.dmg`
- [ ] 4.4 Собрать `.dmg` двух последовательных версий и вручную проверить, что установка второй поверх первой заменяет приложение, а не создаёт вторую копию, и что данные из `Application Support` сохраняются

## 5. Проверка обновлений

- [ ] 5.1 Определить формат источника версии (статический JSON с полями `latestVersion` и `downloadUrl`, публикуемый вместе с релизом)
- [ ] 5.2 Реализовать проверку версии при старте приложения (HTTP-запрос с таймаутом, без блокировки запуска при ошибке/недоступности сети)
- [ ] 5.3 Показать уведомление в UI, если доступна более новая версия, со ссылкой на скачивание
- [ ] 5.4 Проверить сценарии: актуальная версия (без уведомления), новая версия доступна (уведомление показывается), сеть недоступна (приложение стартует без ошибок)
