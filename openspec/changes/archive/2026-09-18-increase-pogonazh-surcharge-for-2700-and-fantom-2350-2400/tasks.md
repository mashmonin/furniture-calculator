## 1. Liquibase-миграция данных

- [x] 1.1 Создать changelog `backend/src/main/resources/db/changelog/changes/0095-pogonazh-surcharge-rule-50-percent-tier.yaml` с changeset'ом `UPDATE pogonazh_surcharge_rule SET surcharge_percent = 50 WHERE value = 2700` (затрагивает существующие строки frame_type FT-002/FT-003, все 9 door_casing_type, frame_extensions_type FET-004..007 — без создания новых строк там, где 2700 не является каталожной длиной).
- [x] 1.2 В том же файле — changeset с `INSERT INTO pogonazh_surcharge_rule (frame_type_id, value, surcharge_percent) SELECT ft.id, v.value, 50 FROM frame_type ft CROSS JOIN (VALUES (2350::numeric), (2400::numeric)) AS v(value) WHERE ft.code = 'FT-001'`.
- [x] 1.3 Подключить `changes/0095-pogonazh-surcharge-rule-50-percent-tier.yaml` в `db.changelog-master.yaml` (без разбивки по context — чистые UPDATE/INSERT работают одинаково в PostgreSQL и H2, desktop-override не нужен, как отмечено в proposal.md).

## 2. Проверка

- [x] 2.1 Точечно прогнать тест(ы), покрывающие миграции desktop-профиля на H2 (`DesktopProfileTest` — см. `--tests` на конкретный класс, не полный `gradlew test`), чтобы убедиться, что новый changeset применяется и в контексте `desktop`. Прогнан точечно (`--tests DesktopProfileTest`) — BUILD SUCCESSFUL, миграция 0095 применяется на H2.
- [x] 2.2 Вручную проверить через `GET /api/pricing-surcharges` (или расчёт стоимости конфигурации с коробом «Компланар»/«НЕО»/«Фантом», value=2700 или 2350/2400 у полотна), что для затронутых значений отдаётся/применяется наценка 50%, а для прочих нестандартных значений — прежние 30%. Проверено пользователем вручную в backend, запущенном из IDE — миграция применилась.
