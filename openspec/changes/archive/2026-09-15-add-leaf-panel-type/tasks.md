## 1. Миграция БД

- [x] 1.1 Новый Liquibase changeset: добавить nullable-колонку `panel_type` (varchar) в таблицу `leaf_type`, подключить в `db.changelog-master.yaml`
- [x] 1.2 В том же changeset — backfill-данные: `UPDATE leaf_type SET panel_type = 'MIRRORED' WHERE id IN (SELECT DISTINCT leaf_type_id FROM mirror_finish_option)`, затем `UPDATE leaf_type SET panel_type = 'BLIND' WHERE panel_type IS NULL`
- [x] 1.3 В том же changeset — установить `NOT NULL` и `CHECK (panel_type IN ('BLIND', 'GLAZED', 'MIRRORED'))` на колонку `panel_type`
- [x] 1.4 Явный `rollback` не добавлен — ни один существующий changeset в проекте (включая другие raw-`sql`/CHECK constraint изменения, напр. `0006-2`) не определяет ручной rollback; сохранена текущая конвенция проекта

## 2. Backend: домен и маппинг

- [x] 2.1 Добавить enum `LeafPanelType { BLIND, GLAZED, MIRRORED }` в `domain`
- [x] 2.2 Добавить поле `panelType` (`@Enumerated(EnumType.STRING)`, `nullable = false`) в `LeafType`
- [x] 2.3 Добавить поле типа полотна в leaf-часть `ComponentCatalogDto` (или в `ReferenceDto` leaf_type — выбрать по месту, сохраняя симметрию с существующими полями)
- [x] 2.4 Заполнить новое поле в `DoorConfigurationCatalogService` только для leaf-компонента; для остальных компонентов оставить `null`

## 3. Тесты

- [x] 3.1 Backend: тест на отклонение вставки `leaf_type` без `panel_type` и со значением вне набора допустимых
- [x] 3.2 Backend: тест на то, что каталог конфигураций (`GET /api/door-configurations`) отдаёт тип полотна для leaf-компонента и не отдаёт его (или `null`) для остальных компонентов
- [x] 3.3 Проверено на dev-БД (`docker compose exec postgres psql`, миграция `0089` уже была применена): 45 строк leaf_type — 31 `MIRRORED` (ровно столько же различных `leaf_type_id` в `mirror_finish_option`) и 14 `BLIND` (0 `GLAZED`, ожидаемо — остеклённых моделей в каталоге пока нет); две сверочные проверки (`MIRRORED` без `mirror_finish_option` и наоборот) вернули 0 строк — расхождений нет
