## Purpose

Определяет сохраняемую структуру конфигурации item — его полотна (leaf), коробку (frame), кромки (edge), опциональную вставку зеркала (leaf_mirror), добор (door_casing), удлинители (frame_extensions), их размеры (liner_dimensions) и цвета (colours), а также справочные типы, которые их ограничивают, — чтобы бизнес-логика калькулятора мебели имела надёжную реляционную основу для дальнейшей разработки.

## ADDED Requirements

### Requirement: Состав item
Item ДОЛЖЕН (SHALL) ссылаться ровно на один item_type и МОЖЕТ (MAY) быть связан с любым количеством leaf, frame и edge.

#### Scenario: Item требует существующий тип
- **Когда** строка item вставляется со ссылкой на item_type, которого не существует
- **То** база данных отклоняет вставку

#### Scenario: У item может быть несколько leaf
- **Когда** вставляются две строки leaf, ссылающиеся на один и тот же item (например, двустворчатая дверь)
- **То** обе строки leaf сохраняются, и обе ссылаются на этот item

### Requirement: Структура leaf
Leaf ДОЛЖЕН (SHALL) ссылаться ровно на один leaf_type, ровно на одну запись dimensions и ровно на один colour, и МОЖЕТ (MAY) быть связан с нулём или более конфигураций leaf_mirror.

#### Scenario: Leaf требует type, dimensions и colour
- **Когда** строка leaf вставляется без ссылки на leaf_type, dimensions или colour
- **То** база данных отклоняет вставку

#### Scenario: Leaf без mirror допустим
- **Когда** leaf вставляется без связанной строки leaf_mirror
- **То** leaf успешно сохраняется

### Requirement: Структура leaf_mirror
Конфигурация leaf_mirror ДОЛЖНА (SHALL) ссылаться ровно на один leaf, ровно на один mirror_type, ровно на один leaf_side и ровно на одну запись dimensions.

#### Scenario: Mirror требует свои leaf, type, side и dimensions
- **Когда** строка leaf_mirror вставляется без ссылки на leaf, mirror_type, leaf_side или dimensions
- **То** база данных отклоняет вставку

### Requirement: Структура frame
Frame ДОЛЖЕН (SHALL) ссылаться ровно на один item, ровно на один frame_type, ровно на одну запись dimensions и ровно на один colour, и МОЖЕТ (MAY) быть связан с нулём или более строк door_casing и нулём или более строк frame_extensions.

#### Scenario: Frame требует свои type, dimensions и colour
- **Когда** строка frame вставляется без ссылки на frame_type, dimensions или colour
- **То** база данных отклоняет вставку

### Requirement: Структура door_casing
Door_casing ДОЛЖЕН (SHALL) ссылаться ровно на один frame, ровно на одну запись dimensions и ровно на один colour.

#### Scenario: Casing требует свои frame, dimensions и colour
- **Когда** строка door_casing вставляется без ссылки на frame, dimensions или colour
- **То** база данных отклоняет вставку

### Requirement: Структура frame_extensions
Frame_extensions ДОЛЖЕН (SHALL) ссылаться ровно на один frame, ровно на одну запись dimensions и ровно на один colour.

#### Scenario: Extension требует свои frame, dimensions и colour
- **Когда** строка frame_extensions вставляется без ссылки на frame, dimensions или colour
- **То** база данных отклоняет вставку

### Requirement: Структура edge
Edge ДОЛЖЕН (SHALL) ссылаться ровно на один item, ровно на одну запись dimensions и ровно на один colour.

#### Scenario: Edge требует свои item, dimensions и colour
- **Когда** строка edge вставляется без ссылки на item, dimensions или colour
- **То** база данных отклоняет вставку

### Requirement: Общие каталоги dimensions и colour
Запись dimensions ДОЛЖНА (SHALL) ссылаться ровно на один тип размеров, а colour ДОЛЖЕН (SHALL) ссылаться ровно на один тип цвета. Обе ДОЛЖНЫ (SHALL) быть переиспользуемыми — на них может ссылаться любое количество строк leaf, leaf_mirror, frame, door_casing, frame_extensions или edge.

#### Scenario: Несколько компонентов используют одну запись dimensions
- **Когда** и строка leaf, и строка frame ссылаются на одну и ту же запись dimensions
- **То** обе сохраняются без конфликта

### Requirement: Защита справочников от удаления
Система ДОЛЖНА (SHALL) предотвращать удаление справочной/типовой строки (item_type, leaf_type, mirror_type, leaf_side, frame_type, dimension_type, colour_type), пока на неё ещё ссылается хотя бы одна строка.

#### Scenario: Используемый тип нельзя удалить
- **Когда** предпринимается попытка удалить leaf_type, пока на него ещё ссылается строка leaf
- **То** база данных отклоняет удаление
