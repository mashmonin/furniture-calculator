## 1. Liquibase: новый наличник и исправление данных

- [x] 1.1 Новый changeset-файл `0029-komplanar-reverse-avenue-casing-fix.yaml`: `insert` нового `door_casing_type` «Авеню для системы КОМПЛАНАР РЕВЕРС \*» (код `DCT-005`)
- [x] 1.2 Changeset: `insert` `liner_dimension_option` (высота 2100 мм, `is_standard = true`) для `door_casing_type_id = DCT-005`, по образцу существующей строки для DCT-002
- [x] 1.3 Changeset: `insert` `configuration_price` (retail_price 3622, dealer_price 2070) для `door_casing_type_id = DCT-005` со ссылкой на его `height_option_id`, по образцу существующей строки для DCT-002
- [x] 1.4 Changeset: `sql` — `UPDATE door_configuration SET door_casing_type_id = <DCT-005> WHERE frame_type_id = <FT-004> AND door_casing_type_id = <DCT-002>`, точечно исправляющий 13 строк, ошибочно заведённых миграцией `0028`
- [x] 1.5 Подключить `0029-komplanar-reverse-avenue-casing-fix.yaml` в `db.changelog-master.yaml`; поднять `docker compose up -d`, прогнать миграцию

## 2. Проверка

- [x] 2.1 Проверить в БД: новый `door_casing_type` DCT-005 существует; 13 `door_configuration` с `frame_type_id = FT-004` теперь ссылаются на DCT-005, а не на DCT-002; связи DCT-002 с обычным коробом КОМПЛАНАР (FT-002) не затронуты — подтверждено (DCT-002: 189 конфигураций, все FT-002; DCT-005: 13 конфигураций, все FT-004)
- [x] 2.2 Ручная проверка через API (backend перезапущен): каталог конфигураций для реверс-моделей отдаёт наличник «Авеню для системы КОМПЛАНАР РЕВЕРС \*» вместо «Авеню» (13 конфигураций, все с FT-004); расчёт стоимости (id=779) даёт наличник 3622/2070 ₽ — та же цена, что раньше давал обычный «Авеню»; итог конфигурации не изменился (47165/28716 ₽)
