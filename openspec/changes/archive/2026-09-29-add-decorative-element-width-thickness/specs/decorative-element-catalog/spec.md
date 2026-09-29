## MODIFIED Requirements

### Requirement: Структура decorative_element_type
Decorative_element_type ДОЛЖЕН (SHALL) ссылаться ровно на одну decorative_element_category и иметь непустое название (name), код (code, уникальный в пределах справочника), длину в миллиметрах (length_mm), розничную (retail_price) и дилерскую (dealer_price) цену — все пять полей обязательны. Дополнительно decorative_element_type МОЖЕТ (MAY) иметь ширину (width_mm) и толщину (thickness_mm) в миллиметрах — оба поля необязательны (nullable) и задаются только там, где эти размеры имеют смысл для категории. Тип описывает готовую позицию целиком (например, «Плинтус Модо», длина 2400 мм) — в отличие от фурнитуры, у decorative_element_type нет собственных цветовых вариантов с отдельными ценами.

#### Scenario: Тип требует категорию, name, code, длину и обе цены
- **WHEN** строка decorative_element_type вставляется без ссылки на decorative_element_category, без name, без code, без length_mm, без retail_price или без dealer_price
- **THEN** база данных отклоняет вставку

#### Scenario: У одной категории может быть несколько типов
- **WHEN** для одной decorative_element_category вставляются несколько строк decorative_element_type с разными name/code
- **THEN** все строки сохраняются и все ссылаются на эту категорию

#### Scenario: Ширина и толщина необязательны
- **WHEN** строка decorative_element_type вставляется без width_mm и без thickness_mm
- **THEN** база данных принимает вставку, width_mm и thickness_mm сохраняются как NULL

## ADDED Requirements

### Requirement: Ширина и толщина типов категории «Блок и база»
Каждый decorative_element_type категории «Блок и база» (`DEC-002`) ДОЛЖЕН (SHALL) иметь заполненные width_mm и thickness_mm со следующими значениями:

| Тип | width_mm | thickness_mm |
|---|---|---|
| «Блок А» | 90 | 30 |
| «База А» | 90 | 30 |
| «Блок В» | 91 | 30 |
| «База В» | 91 | 31 |
| «База В КОМПЛАНАР» | 91 | 31 |

Для decorative_element_type категории «Плинтус» (`DEC-001`) width_mm и thickness_mm остаются NULL.

#### Scenario: Блок и база хранят ширину и толщину
- **WHEN** запрашивается строка decorative_element_type с кодом одним из `DET-003`…`DET-007`
- **THEN** её width_mm и thickness_mm равны значениям из таблицы выше

#### Scenario: Плинтус не имеет ширины и толщины
- **WHEN** запрашивается строка decorative_element_type с кодом `DET-001` или `DET-002`
- **THEN** её width_mm и thickness_mm равны NULL
