## Purpose

Определяет сохраняемую структуру конфигурации item — его полотна (leaf), коробку (frame), кромки (edge), опциональную вставку зеркала (leaf_mirror), добор (door_casing), удлинители (frame_extensions), их размеры (liner_dimensions) и цвета (colours), а также справочные типы, которые их ограничивают, — чтобы бизнес-логика калькулятора мебели имела надёжную реляционную основу для дальнейшей разработки.

## Requirements

### Requirement: Состав item
Item ДОЛЖЕН (SHALL) ссылаться ровно на один item_type и МОЖЕТ (MAY) быть связан с любым количеством leaf, frame и edge.

#### Scenario: Item требует существующий тип
- **Когда** строка item вставляется со ссылкой на item_type, которого не существует
- **То** база данных отклоняет вставку

#### Scenario: У item может быть несколько leaf
- **Когда** вставляются две строки leaf, ссылающиеся на один и тот же item (например, двустворчатая дверь)
- **То** обе строки leaf сохраняются, и обе ссылаются на этот item

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
Door_casing ДОЛЖЕН (SHALL) ссылаться ровно на один frame и ровно на один door_casing_type, и МОЖЕТ (MAY) быть связан с нулём или более записей liner_dimensions и нулём или более записей colours. Каждая связанная запись liner_dimensions/colours принадлежит только этому door_casing.

#### Scenario: Casing требует свои frame, dimensions и colour
- **Когда** строка door_casing вставляется без ссылки на frame
- **То** база данных отклоняет вставку

#### Scenario: Casing требует существующий door_casing_type
- **Когда** строка door_casing вставляется без ссылки на door_casing_type или со ссылкой на door_casing_type, которого не существует
- **То** база данных отклоняет вставку

### Requirement: Структура frame_extensions
Frame_extensions ДОЛЖЕН (SHALL) ссылаться ровно на один frame и ровно на один frame_extensions_type, и МОЖЕТ (MAY) быть связан с нулём или более записей liner_dimensions и нулём или более записей colours. Каждая связанная запись liner_dimensions/colours принадлежит только этому frame_extensions.

#### Scenario: Extension требует свои frame, dimensions и colour
- **Когда** строка frame_extensions вставляется без ссылки на frame
- **То** база данных отклоняет вставку

#### Scenario: Extension требует существующий frame_extensions_type
- **Когда** строка frame_extensions вставляется без ссылки на frame_extensions_type или со ссылкой на frame_extensions_type, которого не существует
- **То** база данных отклоняет вставку

### Requirement: Структура edge
Edge ДОЛЖЕН (SHALL) ссылаться ровно на один item и ровно на один edge_type, и МОЖЕТ (MAY) быть связан с нулём или более записей liner_dimensions и нулём или более записей colours. Каждая связанная запись liner_dimensions/colours принадлежит только этому edge.

#### Scenario: Edge требует свои item, dimensions и colour
- **Когда** строка edge вставляется без ссылки на item
- **То** база данных отклоняет вставку

#### Scenario: Edge требует существующий edge_type
- **Когда** строка edge вставляется без ссылки на edge_type или со ссылкой на edge_type, которого не существует
- **То** база данных отклоняет вставку

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

### Requirement: Защита справочников от удаления
Система ДОЛЖНА (SHALL) предотвращать удаление справочной/типовой строки (item_type, leaf_type, mirror_type, leaf_side, frame_type, liner_dimension_type, colour_type, edge_type, door_casing_type, frame_extensions_type), пока на неё ещё ссылается хотя бы одна строка.

#### Scenario: Используемый тип нельзя удалить
- **Когда** предпринимается попытка удалить leaf_type, пока на него ещё ссылается строка leaf
- **То** база данных отклоняет удаление

#### Scenario: Используемый edge_type нельзя удалить
- **Когда** предпринимается попытка удалить edge_type, пока на него ещё ссылается строка edge
- **То** база данных отклоняет удаление

### Requirement: Атрибуты справочных значений
Каждая справочная/типовая строка (item_type, leaf_type, mirror_type, leaf_side, frame_type, liner_dimension_type, colour_type, edge_type, door_casing_type, frame_extensions_type) ДОЛЖНА (SHALL) иметь непустое человекочитаемое название (name) и непустой код (code), уникальный в пределах своей таблицы.

#### Scenario: Справочная строка требует name и code
- **Когда** строка item_type (или любой другой справочной таблицы) вставляется без name или без code
- **То** база данных отклоняет вставку

#### Scenario: Код справочной строки уникален
- **Когда** вставляется вторая строка leaf_type (или любой другой справочной таблицы) с уже существующим значением code
- **То** база данных отклоняет вставку

### Requirement: Атрибуты liner_dimensions
Запись liner_dimensions ДОЛЖНА (SHALL) хранить числовое значение размера (value) и признак того, что это стандартный (каталожный) размер (is_standard); оба поля обязательны.

#### Scenario: liner_dimensions требует value и is_standard
- **Когда** строка liner_dimensions вставляется без value или без is_standard
- **То** база данных отклоняет вставку

### Requirement: Атрибуты colours
Запись colours ДОЛЖНА (SHALL) хранить непустое название цвета (name); каталожный код цвета (ral_code) необязателен.

#### Scenario: colours требует name
- **Когда** строка colours вставляется без name
- **То** база данных отклоняет вставку

#### Scenario: ral_code необязателен
- **Когда** строка colours вставляется с name, но без ral_code
- **То** строка успешно сохраняется
