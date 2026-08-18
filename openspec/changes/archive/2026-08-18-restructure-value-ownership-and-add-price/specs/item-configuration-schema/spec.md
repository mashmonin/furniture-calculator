## MODIFIED Requirements

### Requirement: Структура leaf
Leaf ДОЛЖЕН (SHALL) ссылаться ровно на один leaf_type, и МОЖЕТ (MAY) быть связан с нулём или более записей liner_dimensions, нулём или более записей colours и нулём или более конфигураций leaf_mirror. Каждая связанная запись liner_dimensions/colours принадлежит только этому leaf (dimensions и colour больше не обязательны через единственную FK — см. «Владение liner_dimensions и colours»).

#### Scenario: Leaf требует type, dimensions и colour
- **Когда** строка leaf вставляется без ссылки на leaf_type
- **То** база данных отклоняет вставку

#### Scenario: Leaf может иметь несколько записей liner_dimensions
- **Когда** для одного leaf вставляются несколько строк liner_dimensions (например, длина и высота), каждая со своим liner_dimension_type и с leaf_id, указывающим на этот leaf
- **То** все строки сохраняются и все принадлежат этому leaf

#### Scenario: Leaf без mirror допустим
- **Когда** leaf вставляется без связанной строки leaf_mirror
- **То** leaf успешно сохраняется

### Requirement: Структура leaf_mirror
Конфигурация leaf_mirror ДОЛЖНА (SHALL) ссылаться ровно на один leaf, ровно на один mirror_type и ровно на один leaf_side, и МОЖЕТ (MAY) быть связана с нулём или более записей liner_dimensions. Каждая связанная запись liner_dimensions принадлежит только этой конфигурации leaf_mirror.

#### Scenario: Mirror требует свои leaf, type, side и dimensions
- **Когда** строка leaf_mirror вставляется без ссылки на leaf, mirror_type или leaf_side
- **То** база данных отклоняет вставку

### Requirement: Структура frame
Frame ДОЛЖЕН (SHALL) ссылаться ровно на один item, ровно на один frame_type, и МОЖЕТ (MAY) быть связан с нулём или более записей liner_dimensions, нулём или более записей colours, нулём или более строк door_casing и нулём или более строк frame_extensions. Каждая связанная запись liner_dimensions/colours принадлежит только этому frame.

#### Scenario: Frame требует свои type, dimensions и colour
- **Когда** строка frame вставляется без ссылки на frame_type
- **То** база данных отклоняет вставку

#### Scenario: Frame может иметь несколько размеров одновременно
- **Когда** для одного frame вставляются три строки liner_dimensions с разными liner_dimension_type (длина, ширина, высота), каждая с frame_id, указывающим на этот frame
- **То** все три строки сохраняются и все принадлежат этому frame

### Requirement: Структура door_casing
Door_casing ДОЛЖЕН (SHALL) ссылаться ровно на один frame, и МОЖЕТ (MAY) быть связан с нулём или более записей liner_dimensions и нулём или более записей colours. Каждая связанная запись liner_dimensions/colours принадлежит только этому door_casing.

#### Scenario: Casing требует свои frame, dimensions и colour
- **Когда** строка door_casing вставляется без ссылки на frame
- **То** база данных отклоняет вставку

### Requirement: Структура frame_extensions
Frame_extensions ДОЛЖЕН (SHALL) ссылаться ровно на один frame, и МОЖЕТ (MAY) быть связан с нулём или более записей liner_dimensions и нулём или более записей colours. Каждая связанная запись liner_dimensions/colours принадлежит только этому frame_extensions.

#### Scenario: Extension требует свои frame, dimensions и colour
- **Когда** строка frame_extensions вставляется без ссылки на frame
- **То** база данных отклоняет вставку

### Requirement: Структура edge
Edge ДОЛЖЕН (SHALL) ссылаться ровно на один item, и МОЖЕТ (MAY) быть связан с нулём или более записей liner_dimensions и нулём или более записей colours. Каждая связанная запись liner_dimensions/colours принадлежит только этому edge.

#### Scenario: Edge требует свои item, dimensions и colour
- **Когда** строка edge вставляется без ссылки на item
- **То** база данных отклоняет вставку

## REMOVED Requirements

### Requirement: Общие каталоги dimensions и colour
**Reason**: Переиспользование одной строки liner_dimensions/colours несколькими компонентами противоречит новой модели владения, в которой каждая строка liner_dimensions/colours принадлежит ровно одному компоненту (см. «Владение liner_dimensions и colours»).
**Migration**: Данные не затронуты — таблицы liner_dimensions/colours в разработческой базе пусты на момент этого изменения.

Запись dimensions ДОЛЖНА (SHALL) ссылаться ровно на один тип размеров, а colour ДОЛЖЕН (SHALL) ссылаться ровно на один тип цвета. Обе ДОЛЖНЫ (SHALL) быть переиспользуемыми — на них может ссылаться любое количество строк leaf, leaf_mirror, frame, door_casing, frame_extensions или edge.

#### Scenario: Несколько компонентов используют одну запись dimensions
- **Когда** и строка leaf, и строка frame ссылаются на одну и ту же запись dimensions
- **То** обе сохраняются без конфликта

## ADDED Requirements

### Requirement: Владение liner_dimensions и colours
Каждая запись liner_dimensions ДОЛЖНА (SHALL) принадлежать ровно одному владельцу среди leaf, frame, edge, door_casing, frame_extensions, leaf_mirror. Каждая запись colours ДОЛЖНА (SHALL) принадлежать ровно одному владельцу среди leaf, frame, edge, door_casing, frame_extensions. Один компонент МОЖЕТ (MAY) владеть любым количеством записей liner_dimensions/colours; запись, однажды созданная, не может быть переиспользована другим компонентом.

#### Scenario: Запись liner_dimensions требует ровно одного владельца
- **Когда** строка liner_dimensions вставляется без ссылки ни на один из возможных владельцев, или со ссылками сразу на двух
- **То** база данных отклоняет вставку

#### Scenario: Запись colours требует ровно одного владельца
- **Когда** строка colours вставляется без ссылки ни на один из возможных владельцев, или со ссылками сразу на двух
- **То** база данных отклоняет вставку

### Requirement: Сущность price
Запись price ДОЛЖНА (SHALL) хранить розничную цену (retail_price) и дилерскую цену (dealer_price), обе обязательны, и ДОЛЖНА (SHALL) принадлежать ровно одному владельцу среди item, leaf, frame, edge, leaf_mirror, door_casing, frame_extensions. Один элемент МОЖЕТ (MAY) иметь любое количество записей price (например, историю изменения цены).

#### Scenario: Price требует retail_price и dealer_price
- **Когда** строка price вставляется без retail_price или без dealer_price
- **То** база данных отклоняет вставку

#### Scenario: Price требует ровно одного владельца
- **Когда** строка price вставляется без ссылки ни на один из возможных владельцев, или со ссылками сразу на двух
- **То** база данных отклоняет вставку

#### Scenario: У одного элемента может быть несколько записей price
- **Когда** для одного и того же item вставляются две строки price с разными значениями retail_price
- **То** обе строки сохраняются
