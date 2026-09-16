## 1. Краткие наименования наличников КОМПЛАНАР РЕВЕРС

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0092-door-casing-type-komplanar-reverse-short-names.yaml`: `UPDATE door_casing_type SET short_name = 'Авеню' WHERE code = 'DCT-005'`, аналогично `short_name = 'Аура' WHERE code = 'DCT-007'`, `short_name = 'Ария' WHERE code = 'DCT-009'`.
- [x] 1.2 Подключить changeset в `db.changelog-master.yaml`.

## 2. Соответствие вида кромки и признака реверса

- [x] 2.1 На локальной БД (`docker compose up -d`, применённые миграции) выполнить `SELECT count(*) FROM door_configuration dc JOIN edge_type et ON et.id = dc.edge_type_id WHERE et.code = 'ET-001' AND dc.is_reverse = true` и аналогично для `ET-002 AND is_reverse = false` — зафиксировать числа затрагиваемых строк.
- [x] 2.2 Для каждой пары (`leaf_type_id`, `frame_type_id`, `is_reverse`), имеющей строки с «неправильной» кромкой, проверить SQL-запросом, что после удаления для неё остаётся хотя бы одна строка `door_configuration` с «правильной» кромкой (см. design.md, «Риски») — не более пары дополнительных запросов, не полный аудит.
- [x] 2.3 Создать `backend/src/main/resources/db/changelog/changes/0093-edge-type-reverse-rule.yaml`: `DELETE FROM door_configuration WHERE edge_type_id = (SELECT id FROM edge_type WHERE code = 'ET-001') AND is_reverse = true`, затем аналогичный `DELETE ... code = 'ET-002' AND is_reverse = false`.
- [x] 2.4 Подключить changeset в `db.changelog-master.yaml`.
- [x] 2.5 Пересоздать локальную БД миграциями с нуля (или применить только новый changeset) и повторить запросы из 2.1 — убедиться, что оба count теперь равны 0.

## 3. Отображение позиции короба «Комплект зарезных стоек»

- [x] 3.1 В `frontend/src/App.tsx`, в месте, где для каждой позиции `frame_post` строится строка `{post.postType.name} × {post.quantity}{post.length !== null ? `, длина ${post.length}` : ''}`, не добавлять суффикс длины, если `post.postType.name === 'Комплект зарезных стоек'` — для всех прочих post_type поведение не меняется.

## 4. Проверка

- [x] 4.1 `./gradlew compileJava` в `backend/` — без ошибок (миграции не требуют кода, но подключение changeset'ов в мастер-файл должно быть синтаксически корректным; фактическая проверка данных — через `bootRun` с локальной БД, см. 2.1/2.5).
- [x] 4.2 `npx tsc -b` и `npm run lint` во `frontend/` — без ошибок.
- [x] 4.3 Вручную через `npm run dev` (с поднятым backend и применёнными миграциями): позиция «Комплект зарезных стоек» в карточке короба показывается без длины; наличники «Авеню для системы КОМПЛАНАР РЕВЕРС \*» / «Аура ...» / «Ария ...» отображаются с кратким наименованием там, где короткие имена уже используются в UI; для конфигурации без реверса шаг выбора кромки предлагает только прямую кромку, для реверсивной — только с четвертью.
