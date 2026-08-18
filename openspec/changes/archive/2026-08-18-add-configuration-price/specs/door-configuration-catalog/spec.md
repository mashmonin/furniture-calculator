## ADDED Requirements

### Requirement: Структура configuration_price
Configuration_price ДОЛЖЕН (SHALL) хранить retail_price и dealer_price (оба обязательны) и принадлежать ровно одному типу компонента среди leaf_type, frame_type, edge_type, door_casing_type, frame_extensions_type. Запись МОЖЕТ (MAY) ссылаться на конкретное значение длины, высоты и толщины (каждое — отдельная необязательная ссылка на liner_dimension_option) и на конкретный цвет (необязательная ссылка на colour_option).

#### Scenario: Price требует retail_price и dealer_price
- **Когда** строка configuration_price вставляется без retail_price или без dealer_price
- **То** база данных отклоняет вставку

#### Scenario: Price может не ссылаться на все виды размера
- **Когда** строка configuration_price вставляется со ссылкой только на длину, без ссылок на высоту и толщину
- **То** строка успешно сохраняется

#### Scenario: Price может не ссылаться на цвет
- **Когда** строка configuration_price вставляется без ссылки на colour_option
- **То** строка успешно сохраняется

### Requirement: Владение configuration_price
Каждая запись configuration_price ДОЛЖНА (SHALL) принадлежать ровно одному владельцу среди leaf_type, frame_type, edge_type, door_casing_type, frame_extensions_type.

#### Scenario: Запись configuration_price требует ровно одного владельца
- **Когда** строка configuration_price вставляется без ссылки ни на один из возможных владельцев, или со ссылками сразу на двух
- **То** база данных отклоняет вставку

### Requirement: Уникальность configuration_price
Комбинация владельца, ссылки на длину, ссылки на высоту, ссылки на толщину и ссылки на цвет ДОЛЖНА (SHALL) быть уникальной — включая случаи, когда одна или несколько из этих ссылок не заданы.

#### Scenario: Повторная цена на ту же комбинацию отклоняется
- **Когда** вставляется вторая строка configuration_price с точно такими же ссылками на владельца, длину, высоту, толщину и цвет (в том числе если какие-то из них одинаково не заданы)
- **То** база данных отклоняет вставку
