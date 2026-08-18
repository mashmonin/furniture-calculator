## Context

См. proposal.md — Why. Сейчас `liner_dimensions`/`colours` — общие таблицы значений: `leaf`, `frame`, `edge`, `door_casing`, `frame_extensions` (и `leaf_mirror` — только для liner_dimensions) хранят единственную FK на одну строку `liner_dimensions`/`colours`, и эта строка теоретически может быть переиспользована несколькими компонентами. Такая форма физически не позволяет одному `frame` иметь длину, ширину и высоту одновременно — это разные строки `liner_dimensions`, а колонка на `frame` только одна.

Уже применённые `0001-item-schema.yaml` и `0002-item-attribute-columns.yaml` не редактируются — эта схема меняется новым additive/dropColumn changeset'ом поверх них.

## Goals / Non-Goals

**Goals:**
- Позволить одному компоненту (`leaf`, `frame`, `edge`, `door_casing`, `frame_extensions`, `leaf_mirror`) иметь произвольное количество записей `liner_dimensions` и `colours` (кроме `leaf_mirror` — без `colours`, как и раньше).
- Добавить сущность `price`, привязываемую к любой из семи структурных сущностей, с поддержкой нескольких цен на элемент.
- Сохранить целостность через настоящие FK (никакого `binding_object_id`/полиморфных колонок без целевой таблицы) — см. Decisions.

**Non-Goals:**
- JPA-сущности, репозитории, сервисы, REST-эндпоинты — не в этом изменении.
- Начальные/тестовые данные — не в этом изменении.
- Ограничение «не более одной записи price на элемент» — намеренно не вводится (см. Decisions).

## Decisions

### Владение вместо переиспользования: несколько nullable FK-колонок + CHECK, а не join-таблица и не единая полиморфная колонка
Рассматривались три формы:
1. **`binding_object_id` на `liner_dimensions`/`colours`/`price`** (одна generic-колонка) — отклонено: одна колонка в Postgres может ссылаться только на одну целевую таблицу. Без дискриминатора `binding_object_id` неоднозначен (id=5 — это frame №5 или leaf №5?), а с дискриминатором Postgres всё равно не проверит существование строки в правильной таблице — реальной FK-целостности нет.
2. **Join-таблица на каждую пару** (`frame_liner_dimensions`, `leaf_liner_dimensions` и т. д.) — технически корректна и поддерживает переиспользование, но переиспользование в этом изменении сознательно убирается (см. proposal.md), а без него join-таблица — лишняя косвенность.
3. **Несколько nullable FK-колонок на самой `liner_dimensions`/`colours`/`price`, по одной на каждого возможного владельца, плюс `CHECK` на ровно одну заполненную** («exclusive arc») — выбрано. Каждая колонка — обычный, полноценно проверяемый FK на свою таблицу; `CHECK` гарантирует ровно одного владельца на уровне БД.

`CHECK` для `liner_dimensions` (аналогично для `colours` с пятью колонками и `price` с семью):
```sql
ALTER TABLE liner_dimensions ADD CONSTRAINT chk_liner_dimensions_single_owner CHECK (
  (CASE WHEN leaf_id IS NOT NULL THEN 1 ELSE 0 END) +
  (CASE WHEN frame_id IS NOT NULL THEN 1 ELSE 0 END) +
  (CASE WHEN edge_id IS NOT NULL THEN 1 ELSE 0 END) +
  (CASE WHEN door_casing_id IS NOT NULL THEN 1 ELSE 0 END) +
  (CASE WHEN frame_extensions_id IS NOT NULL THEN 1 ELSE 0 END) +
  (CASE WHEN leaf_mirror_id IS NOT NULL THEN 1 ELSE 0 END) = 1
);
```
Liquibase-абстракция (`createTable`/`addColumn`) не выражает многоколоночный `CHECK` напрямую — реализуется через changeType `sql` с обычным `ALTER TABLE ... ADD CONSTRAINT`.

### price не получает ограничение «одна запись на владельца»
В отличие от `liner_dimensions`/`colours` (где несколько строк на владельца — это именно разные размеры/цвета), для `price` несколько строк на владельца осмысленны сами по себе (история цены), поэтому `UNIQUE` на владельческих колонках не добавляется.

### Отказ от строгого NOT NULL «у компонента обязательно должна быть liner_dimensions/colours»
Раньше `leaf.liner_dimensions_id NOT NULL` гарантировал на уровне БД, что у leaf есть размер. При развороте связи (владелец — сторона `liner_dimensions`, не `leaf`) эта гарантия «у каждого leaf есть хотя бы одна запись liner_dimensions» теряется: Postgres не может декларативно потребовать от родителя «у тебя обязательно должен быть хотя бы один потомок» без триггера. Это принято как осознанный компромисс (см. Risks) — обязательность размера/цвета переносится на прикладной уровень, если понадобится.

### Организация changeset'ов
Новый файл `backend/src/main/resources/db/changelog/changes/0003-value-ownership-and-price.yaml`, подключаемый после `0002-item-attribute-columns.yaml`. Порядок внутри:
1. `dropColumn` — убрать `liner_dimensions_id` с `leaf`, `frame`, `edge`, `door_casing`, `frame_extensions`, `leaf_mirror`.
2. `dropColumn` — убрать `colours_id` с `leaf`, `frame`, `edge`, `door_casing`, `frame_extensions`.
3. `addColumn` — добавить владельческие FK-колонки на `liner_dimensions` (6 колонок) и `colours` (5 колонок).
4. `sql` — добавить `CHECK`-ограничения «ровно один владелец» на `liner_dimensions` и `colours`.
5. `createTable` — создать `price` (`id`, `retail_price` DECIMAL NOT NULL, `dealer_price` DECIMAL NOT NULL, 7 владельческих FK-колонок).
6. `sql` — добавить `CHECK`-ограничение «ровно один владелец» на `price`.

## Risks / Trade-offs

- **Потеря DB-уровневой гарантии «у компонента есть хотя бы один размер/цвет»** → раньше это был `NOT NULL` FK, теперь — ничем не ограничено на уровне БД (см. Decisions). Смягчение: если это станет проблемой, добавить триггер или перенести проверку в прикладной слой на этапе, когда появится JPA/сервисный код.
- **`CHECK` на «ровно один владелец» не защищает от одновременного изменения двух колонок в одной транзакции так, чтобы обе оказались NULL или обе заполнены не в момент вставки, а при последующем `UPDATE`** → `CHECK` в Postgres выполняется на каждый `INSERT`/`UPDATE`, так что это уже покрыто; отдельно указано, чтобы не полагаться на проверку только при вставке.
- **BREAKING изменение формы данных** (см. proposal.md Impact) → на момент этого изменения нет прикладного кода и нет значимых данных в разработческой БД, риск регрессии минимален.

## Migration Plan

`dropColumn`/`addColumn`/`sql`-changeset поверх пустых (после верификации предыдущих изменений) таблиц — данных для миграции нет. Откат — `liquibase rollback-count 1` на каждый changeset либо ручное восстановление предыдущей формы колонок в обратном порядке.
