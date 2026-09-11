## 1. Frontend: шаг +50 для длины/высоты полотна

- [x] 1.1 `frontend/src/App.tsx`: добавить `step={50}` в `InputNumber` поля «Другое значение длины (мм)» (`customLengthValueMm`, `step.key === 'leaf'`).
- [x] 1.2 Добавить `step={50}` в `InputNumber` поля «Другое значение высоты (мм)» (`customHeightValueMm`, `step.key === 'leaf'`).

## 2. Frontend: выпадающий список для цвета полотна

- [x] 2.1 `frontend/src/components/OptionGroup.tsx`: добавить необязательный проп `variant?: 'buttons' | 'select'` (по умолчанию `'buttons'`, поведение существующих вызовов не меняется).
- [x] 2.2 При `variant === 'select'` рендерить `antd Select` (`allowClear`, значение — `selectedId`, `options` — из `SelectableOption[]`, `onChange` — как у существующих вызовов) вместо `Radio.Group`/`Radio.Button`; сохранить общее поведение скрытия при пустом списке опций и заголовок группы.
- [x] 2.3 `frontend/src/App.tsx`: в рендере группы «Цвет» передавать `variant="select"` только когда `step.key === 'leaf'`; для остальных компонентов — без изменений.

## 3. Liquibase-миграция: перенос оси наличника

- [x] 3.1 Определить следующий свободный номер миграции в `backend/src/main/resources/db/changelog/changes/` и создать файл (например, `00XX-door-casing-length-axis.yaml`).
- [x] 3.2 Changeset 1: `UPDATE liner_dimension_option SET liner_dimension_type_id = (SELECT id FROM liner_dimension_type WHERE code = 'DT-001') WHERE door_casing_type_id IS NOT NULL AND liner_dimension_type_id = (SELECT id FROM liner_dimension_type WHERE code = 'DT-002')`.
- [x] 3.3 Changeset 2: `UPDATE configuration_price SET length_option_id = height_option_id, height_option_id = NULL WHERE door_casing_type_id IS NOT NULL AND height_option_id IS NOT NULL`.
- [x] 3.4 Подключить новый файл в `db.changelog-master.yaml` (без desktop-оверрайда — обычный `UPDATE` без функциональных/partial-индексов, H2-совместим).

## 4. Проверка

- [x] 4.1 `npm run build` (из `frontend/`) — проверка типов и сборки.
- [x] 4.2 Ручная проверка в браузере: шаг `+50` в полях «Другое значение» длины/высоты полотна (стрелки и колесо мыши); группа «Цвет» полотна — выпадающий список, выбор и очистка работают; группа «Цвет» короба/кромки/наличника/добора — по-прежнему ряд кнопок; наличник показывает значение 2100 мм в группе «Длина», группа «Высота» для наличника не отображается; расчёт стоимости с выбранным наличником по-прежнему находит цену. Подтверждено пользователем.
