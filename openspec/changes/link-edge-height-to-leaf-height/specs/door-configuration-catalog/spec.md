## MODIFIED Requirements

### Requirement: Атрибуты liner_dimension_option
Запись liner_dimension_option ДОЛЖНА (SHALL) хранить числовое значение размера (value) и признак того, что это стандартное (каталожное) значение (is_standard); оба поля обязательны. Запись МОЖЕТ (MAY) дополнительно хранить min_value — нижнюю границу диапазона, для которого value является верхней границей; отсутствие min_value означает открытую (неограниченную) нижнюю границу. Комбинация владельца, liner_dimension_type и value ДОЛЖНА (SHALL) быть уникальной.

#### Scenario: liner_dimension_option требует value и is_standard
- **Когда** строка liner_dimension_option вставляется без value или без is_standard
- **То** база данных отклоняет вставку

#### Scenario: Значение не может повторяться для одного владельца и типа размера
- **Когда** для одного и того же leaf_type и одного и того же liner_dimension_type вставляется вторая строка liner_dimension_option с уже существующим value
- **То** база данных отклоняет вставку

#### Scenario: liner_dimension_option может не иметь min_value
- **Когда** строка liner_dimension_option вставляется без значения min_value
- **То** строка успешно сохраняется, а её диапазон считается открытым снизу (например, «до value»)

#### Scenario: liner_dimension_option может задавать диапазон через min_value и value
- **Когда** строка liner_dimension_option вставляется со значениями min_value и value, где min_value меньше value (например, min_value 2150, value 2400)
- **То** строка успешно сохраняется и описывает диапазон [min_value, value]
