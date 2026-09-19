## ADDED Requirements

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
