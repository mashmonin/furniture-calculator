## Purpose

Определяет справочник коллекций полотна двери (collection) и обязательную принадлежность каждой модели полотна (leaf_type) ровно одной коллекции.

## Requirements

### Requirement: Структура collection
Collection ДОЛЖЕН (SHALL) иметь непустое человекочитаемое название (name) и непустой код (code), уникальный в пределах справочника collection.

#### Scenario: Collection требует name и code
- **WHEN** строка collection вставляется без name или без code
- **THEN** база данных отклоняет вставку

#### Scenario: Code коллекции уникален
- **WHEN** вставляется вторая строка collection с уже существующим code
- **THEN** база данных отклоняет вставку

### Requirement: Принадлежность leaf_type коллекции
Каждый leaf_type ДОЛЖЕН (SHALL) ссылаться ровно на одну collection.

#### Scenario: Leaf_type требует существующую collection
- **WHEN** строка leaf_type вставляется без ссылки на collection или со ссылкой на collection, которой не существует
- **THEN** база данных отклоняет вставку

#### Scenario: Несколько leaf_type могут принадлежать одной коллекции
- **WHEN** для одной collection вставляются несколько строк leaf_type
- **THEN** все строки сохраняются и все ссылаются на эту collection

### Requirement: Защита от удаления связанной коллекции
Система ДОЛЖНА (SHALL) предотвращать удаление строки collection, пока на неё ещё ссылается хотя бы один leaf_type.

#### Scenario: Удаление коллекции с существующими моделями отклоняется
- **WHEN** предпринимается попытка удалить collection, пока на неё ещё ссылается строка leaf_type
- **THEN** база данных отклоняет удаление

### Requirement: Диапазон допустимой нестандартной длины/высоты полотна по коллекции
Collection_dimension_range ДОЛЖЕН (SHALL) ссылаться ровно на одну collection и один liner_dimension_type (ось размера — длина или высота) и хранить минимальное и максимальное допустимое значение размера (min_value, max_value) в миллиметрах; все поля обязательны, min_value ДОЛЖЕН (SHALL) быть меньше-либо-равен max_value. Комбинация collection и liner_dimension_type ДОЛЖНА (SHALL) быть уникальной — не более одного диапазона на пару (коллекция, ось). Это каталожный факт о производственных возможностях серии («какие размеры вообще можно произвести»), не связанный с наценкой (dimension_surcharge_rule/pogonazh_surcharge_rule определяют «сколько это стоит» независимо и остаются самостоятельными справочниками).

#### Scenario: Диапазон требует collection, liner_dimension_type, min_value и max_value
- **WHEN** строка collection_dimension_range вставляется без ссылки на collection, без ссылки на liner_dimension_type, без min_value или без max_value
- **THEN** база данных отклоняет вставку

#### Scenario: Минимум не может быть больше максимума
- **WHEN** строка collection_dimension_range вставляется с min_value больше max_value
- **THEN** база данных отклоняет вставку

#### Scenario: На одну пару (коллекция, ось) — не более одного диапазона
- **WHEN** для одной и той же collection и одного и того же liner_dimension_type вставляется вторая строка collection_dimension_range
- **THEN** база данных отклоняет вставку

#### Scenario: Коллекция может иметь диапазоны по разным осям независимо
- **WHEN** для одной collection вставляются строки collection_dimension_range для длины и для высоты с разными min_value/max_value
- **THEN** обе строки сохраняются независимо друг от друга

#### Scenario: Диапазон для коллекции/оси не обязателен
- **WHEN** для collection и liner_dimension_type нет строки collection_dimension_range
- **THEN** это не считается ошибкой — отсутствие диапазона означает, что для этой пары (коллекция, ось) проверка диапазона не выполняется
