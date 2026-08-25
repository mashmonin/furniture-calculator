## 1. Liquibase: справочник collection и связь с leaf_type

- [x] 1.1 Новый changeset-файл: `createTable: collection` (id) + `addColumn` (name VARCHAR NOT NULL, code VARCHAR NOT NULL UNIQUE) — по образцу двухшаговой миграции существующих справочников (`leaf_type` в 0001/0002)
- [x] 1.2 Changeset: `insert` 9 строк collection — Вертикаль, Атмосфера, Элегант, Гармония, Свобода, Геометрия, Сияние, Сибирь, Фантом (name + code)
- [x] 1.3 Changeset: `addColumn: leaf_type.collection_id` (BIGINT, nullable на этом шаге, inline `foreignKeyName`/`references: collection(id)`, без `onDelete` — поведение по умолчанию должно давать RESTRICT)
- [x] 1.4 Changeset(ы): backfill `collection_id` всем 45 существующим строкам `leaf_type` — `UPDATE leaf_type SET collection_id = (SELECT id FROM collection WHERE code = '...') WHERE code IN (...)`, по одному блоку на коллекцию, используя диапазоны code из design.md (LT-001…007 → Вертикаль, LT-008…013 → Атмосфера, LT-014…019 → Элегант, LT-020…025 → Гармония, LT-026…033 → Свобода, LT-034…037 → Геометрия, LT-038 → Сияние, LT-039…044 → Сибирь, LT-045 → Фантом)
- [x] 1.5 Changeset: `addNotNullConstraint` на `leaf_type.collection_id`
- [x] 1.6 Подключить все новые changeset-файлы в `db.changelog-master.yaml` в правильном порядке; поднять `docker compose up -d` и прогнать миграцию (`./gradlew bootRun` или отдельная liquibase-задача), проверить что все 45 строк `leaf_type` получили корректный `collection_id`

## 2. Backend: сущность, связь, каталог

- [x] 2.1 Новая JPA-сущность `LeafCollection` (`@Entity @Table(name = "collection")`) в `domain/`, реализует `CatalogType`, по образцу `LeafType`
- [x] 2.2 В `LeafType` добавить `@ManyToOne(optional = false)` поле `collection` (FK-столбец `collection_id`)
- [x] 2.3 Расширить DTO каталога так, чтобы leaf-компонент включал вложенные данные коллекции (id/code/name) — переиспользовать `ReferenceDto.from(...)` для `LeafCollection`
- [x] 2.4 Обновить `DoorConfigurationCatalogService` (или соответствующий маппинг в `DoorConfigurationDto`/`ComponentCatalogDto`), чтобы для leaf-компонента заполнялось поле коллекции
- [x] 2.5 Обновить/добавить backend-тест на `GET /api/door-configurations`, проверяющий, что ответ содержит данные коллекции для leaf-компонента конфигурации

## 3. Frontend: шаг выбора коллекции

- [x] 3.1 Обновить типы каталога (`frontend/src/api/types.ts`) — добавить поле коллекции (`ReferenceDto`) в тип leaf-компонента
- [x] 3.2 В `App.tsx` добавить состояние выбранной коллекции и новый шаг (перед `leaf`), показывающий все различные коллекции, встречающиеся среди leaf-компонентов загруженного каталога
- [x] 3.3 Отфильтровать варианты существующего шага `leaf` по выбранной коллекции (показывать только модели полотна этой коллекции)
- [x] 3.4 При смене выбранной коллекции сбрасывать выбор `leaf` и всех последующих шагов каскада (включая скрытие показанных ранее опций размеров/цвета и результата расчёта), аналогично сбросу при смене более раннего шага
- [x] 3.5 Отрендерить шаг выбора коллекции через существующий компонент `OptionGroup` (antd `Radio.Group`/`Radio.Button`), сохранив единый стиль с остальными шагами каскада

## 4. Проверка

- [x] 4.1 Backend: `./gradlew test`
- [x] 4.2 Frontend: `npm run lint` и `npm run build`
- [x] 4.3 Ручная проверка в браузере (`docker compose up -d`, `./gradlew bootRun`, `npm run dev`): выбрать коллекцию → модель полотна → далее по каскаду → убедиться, что список моделей полотна на втором шаге ограничен выбранной коллекцией и расчёт стоимости работает как раньше
