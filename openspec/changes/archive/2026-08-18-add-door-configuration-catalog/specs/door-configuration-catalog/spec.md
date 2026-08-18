## Purpose

Определяет каталог согласованных с фабрикой конфигураций межкомнатных дверей — допустимых сочетаний типов компонентов, а также допустимых размеров и цветов для каждого типа компонента, — отдельно от хранения фактических экземпляров и заказов (item-configuration-schema).

## ADDED Requirements

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
Комбинация leaf_type, frame_type, edge_type, door_casing_type и frame_extensions_type в door_configuration ДОЛЖНА (SHALL) быть уникальной — включая случаи, когда один или несколько из необязательных типов не заданы.

#### Scenario: Повторная конфигурация отклоняется
- **Когда** вставляется вторая строка door_configuration с точно такой же комбинацией leaf_type/frame_type/edge_type/door_casing_type/frame_extensions_type (в том числе если оба раза какой-то из необязательных типов не задан)
- **То** база данных отклоняет вставку

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
Запись liner_dimension_option ДОЛЖНА (SHALL) хранить числовое значение размера (value) и признак того, что это стандартное (каталожное) значение (is_standard); оба поля обязательны. Комбинация владельца, liner_dimension_type и value ДОЛЖНА (SHALL) быть уникальной.

#### Scenario: liner_dimension_option требует value и is_standard
- **Когда** строка liner_dimension_option вставляется без value или без is_standard
- **То** база данных отклоняет вставку

#### Scenario: Значение не может повторяться для одного владельца и типа размера
- **Когда** для одного и того же leaf_type и одного и того же liner_dimension_type вставляется вторая строка liner_dimension_option с уже существующим value
- **То** база данных отклоняет вставку

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
