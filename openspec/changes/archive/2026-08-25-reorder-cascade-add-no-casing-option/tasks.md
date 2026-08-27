## 1. Liquibase: вариант «без наличника» для КОМПЛАНАР и НЕО

- [x] 1.1 Новый changeset-файл `0030-komplanar-neo-no-casing-configurations.yaml`: `sql` — `INSERT INTO door_configuration (leaf_type_id, frame_type_id, edge_type_id, frame_extensions_type_id) SELECT DISTINCT leaf_type_id, frame_type_id, edge_type_id, frame_extensions_type_id FROM door_configuration WHERE frame_type_id IN (id короба FT-002) AND door_casing_type_id IS NOT NULL`
- [x] 1.2 Аналогичный `sql`-changeset для короба НЕО (FT-003)
- [x] 1.3 Подключить новый файл в `db.changelog-master.yaml`; поднять `docker compose up -d`, прогнать миграцию
- [x] 1.4 Проверить в БД: количество `door_configuration` для FT-002 выросло с 378 до 567 (189 новых с `door_casing_type_id IS NULL`), для FT-003 — с 270 до 405 (135 новых) — подтверждено; повторный прогон миграции не создаёт дублей (Liquibase не переисполняет применённые changeset'ы, `Run: 0`)

## 2. Frontend: порядок каскада

- [x] 2.1 В `App.tsx` изменить `CASCADE_ORDER` на `['leaf', 'edge', 'frame', 'doorCasing', 'frameExtensions']`
- [x] 2.2 Обновить сопроводительный комментарий над `CASCADE_ORDER`, описывающий текущий порядок и его обоснование
- [x] 2.3 Убедиться, что `COMPONENT_ORDER` (шаг «2. Выберите опции», запрос расчёта стоимости) не изменён — подтверждено, `COMPONENT_ORDER` не тронут

## 3. Проверка

- [x] 3.1 `npm run build` (`tsc -b && vite build`) — без ошибок типов, сборка успешна
- [x] 3.2 Ручная проверка в браузере (`npm run dev`): для коллекции с коробом КОМПЛАНАР или НЕО каскад показывает шаг «Кромка» сразу после «Полотно», затем «Короб», затем «Наличник» (с пунктом «Без наличника» среди вариантов, если для выбранной комбинации он доступен), затем «Добор»; выбор «Без наличника» продолжает каскад и приводит к однозначной конфигурации без карточки «Наличник» на шаге 2; расчёт стоимости работает как раньше — подтверждено пользователем
