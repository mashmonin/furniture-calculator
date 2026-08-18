## MODIFIED Requirements

### Requirement: Структура edge
Edge ДОЛЖЕН (SHALL) ссылаться ровно на один item и ровно на один edge_type, и МОЖЕТ (MAY) быть связан с нулём или более записей liner_dimensions и нулём или более записей colours. Каждая связанная запись liner_dimensions/colours принадлежит только этому edge.

#### Scenario: Edge требует свои item, dimensions и colour
- **Когда** строка edge вставляется без ссылки на item
- **То** база данных отклоняет вставку

#### Scenario: Edge требует существующий edge_type
- **Когда** строка edge вставляется без ссылки на edge_type или со ссылкой на edge_type, которого не существует
- **То** база данных отклоняет вставку

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
