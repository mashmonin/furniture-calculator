## Purpose

Определяет каталог согласованных с фабрикой конфигураций межкомнатных дверей — допустимых сочетаний типов компонентов, а также допустимых размеров и цветов для каждого типа компонента, — отдельно от хранения фактических экземпляров и заказов (item-configuration-schema).

## Requirements

### Requirement: Структура door_configuration
Door_configuration ДОЛЖЕН (SHALL) ссылаться ровно на один leaf_type и МОЖЕТ (MAY) ссылаться на один frame_type, один edge_type, один door_casing_type и один frame_extensions_type. Если door_casing_type или frame_extensions_type заданы, frame_type ДОЛЖЕН (SHALL) быть задан также.

#### Scenario: Configuration требует существующий leaf_type
- **Когда** строка door_configuration вставляется без ссылки на leaf_type или со ссылкой на leaf_type, которого не существует
- **То** база данных отклоняет вставку

#### Scenario: Configuration может не иметь коробку, добор и удлинители
- **Когда** строка door_configuration вставляется только со ссылкой на leaf_type, без frame_type, edge_type, door_casing_type и frame_extensions_type
- **То** строка успешно сохраняется

#### Scenario: Добор без коробки недопустим
- **Когда** строка door_configuration вставляется со ссылкой на door_casing_type, но без ссылки на frame_type
- **То** база данных отклоняет вставку

#### Scenario: Удлинители без коробки недопустимы
- **Когда** строка door_configuration вставляется со ссылкой на frame_extensions_type, но без ссылки на frame_type
- **То** база данных отклоняет вставку

### Requirement: Уникальность door_configuration
Комбинация leaf_type, frame_type, edge_type, door_casing_type, frame_extensions_type и is_reverse в door_configuration ДОЛЖНА (SHALL) быть уникальной — включая случаи, когда один или несколько из необязательных типов не заданы.

#### Scenario: Повторная конфигурация отклоняется
- **Когда** вставляется вторая строка door_configuration с точно такой же комбинацией leaf_type/frame_type/edge_type/door_casing_type/frame_extensions_type/is_reverse (в том числе если оба раза какой-то из необязательных типов не задан)
- **То** база данных отклоняет вставку

#### Scenario: Обычная и реверсивная конфигурации с одинаковыми ссылками не считаются дублем
- **Когда** вставляются две строки door_configuration с одинаковыми leaf_type/frame_type/edge_type/door_casing_type/frame_extensions_type, но разным значением is_reverse
- **То** обе строки успешно сохраняются

### Requirement: Структура признака реверса door_configuration
Door_configuration ДОЛЖЕН (SHALL) иметь булев признак is_reverse со значением по умолчанию false. Строка со значением is_reverse = true ДОЛЖНА (SHALL) ссылаться на frame_type (реверс-исполнение требует заданного короба, как и наличник/добор). Признак принадлежит конкретной конфигурации, а не типу короба или полотна — один и тот же frame_type и leaf_type могут участвовать как в обычных, так и в реверсивных строках door_configuration.

#### Scenario: Реверс по умолчанию выключен
- **WHEN** строка door_configuration вставляется без явного значения is_reverse
- **THEN** строка сохраняется с is_reverse = false

#### Scenario: Реверс без короба недопустим
- **WHEN** строка door_configuration вставляется с is_reverse = true, но без ссылки на frame_type
- **THEN** база данных отклоняет вставку

#### Scenario: Один и тот же короб используется и для обычных, и для реверсивных конфигураций
- **WHEN** для одного frame_type существуют door_configuration как с is_reverse = false, так и с is_reverse = true
- **THEN** обе группы строк успешно сосуществуют, ссылаясь на один и тот же frame_type

### Requirement: Структура liner_dimension_option
Liner_dimension_option ДОЛЖЕН (SHALL) ссылаться ровно на один liner_dimension_type и ровно на один справочник типа компонента среди leaf_type, frame_type, edge_type, door_casing_type, frame_extensions_type. Каждая строка описывает одно допустимое значение размера для этого типа компонента, а не размер конкретного экземпляра.

#### Scenario: Option требует существующий liner_dimension_type
- **Когда** строка liner_dimension_option вставляется без ссылки на liner_dimension_type или со ссылкой на liner_dimension_type, которого не существует
- **То** база данных отклоняет вставку

#### Scenario: У одного типа компонента может быть несколько допустимых значений размера
- **Когда** для одного leaf_type вставляются несколько строк liner_dimension_option с одним liner_dimension_type (например, длины 600, 700 и 800)
- **То** все строки сохраняются и все принадлежат этому leaf_type

#### Scenario: Списки размеров по разным liner_dimension_type независимы
- **Когда** для одного leaf_type заданы длины 600/700/800 и высоты 2000/2100
- **То** допустимой считается любая комбинация длины и высоты из этих списков — совместимость по конкретным тройкам значений не проверяется на уровне базы данных

### Requirement: Владение liner_dimension_option
Каждая запись liner_dimension_option ДОЛЖНА (SHALL) принадлежать ровно одному владельцу среди leaf_type, frame_type, edge_type, door_casing_type, frame_extensions_type.

#### Scenario: Запись liner_dimension_option требует ровно одного владельца
- **Когда** строка liner_dimension_option вставляется без ссылки ни на один из возможных владельцев, или со ссылками сразу на двух
- **То** база данных отклоняет вставку

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

### Requirement: Структура colour_option
Colour_option ДОЛЖЕН (SHALL) ссылаться ровно на один colour_type и ровно на один справочник типа компонента среди leaf_type, frame_type, edge_type, door_casing_type, frame_extensions_type. Каждая строка описывает один допустимый цвет для этого типа компонента.

#### Scenario: Option требует существующий colour_type
- **Когда** строка colour_option вставляется без ссылки на colour_type или со ссылкой на colour_type, которого не существует
- **То** база данных отклоняет вставку

#### Scenario: У одного типа компонента может быть несколько допустимых цветов
- **Когда** для одного leaf_type вставляются несколько строк colour_option с разными colour_type
- **То** все строки сохраняются и все принадлежат этому leaf_type

### Requirement: Владение colour_option
Каждая запись colour_option ДОЛЖНА (SHALL) принадлежать ровно одному владельцу среди leaf_type, frame_type, edge_type, door_casing_type, frame_extensions_type. Комбинация владельца и colour_type ДОЛЖНА (SHALL) быть уникальной.

#### Scenario: Запись colour_option требует ровно одного владельца
- **Когда** строка colour_option вставляется без ссылки ни на один из возможных владельцев, или со ссылками сразу на двух
- **То** база данных отклоняет вставку

#### Scenario: Цвет не может повторяться для одного владельца
- **Когда** для одного и того же leaf_type вставляется вторая строка colour_option с уже существующим colour_type
- **То** база данных отклоняет вставку

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
