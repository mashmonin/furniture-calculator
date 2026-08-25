## MODIFIED Requirements

### Requirement: Получение каталога конфигураций
Система ДОЛЖНА (SHALL) предоставлять эндпоинт, возвращающий все строки door_configuration. Для каждой конфигурации в ответе ДОЛЖНЫ (SHALL) присутствовать данные (id, code, name) её leaf_type вместе с данными (id, code, name) collection, которой принадлежит этот leaf_type, и, только если они заданы у этой конфигурации, — данные её frame_type, edge_type, door_casing_type и frame_extensions_type.

#### Scenario: Конфигурация только с полотном
- **WHEN** запрашивается каталог конфигураций и в базе есть door_configuration только со ссылкой на leaf_type (без frame_type, edge_type, door_casing_type, frame_extensions_type)
- **THEN** в ответе для этой конфигурации присутствуют данные leaf_type, а поля frame/edge/door_casing/frame_extensions отсутствуют или равны null

#### Scenario: Конфигурация с коробкой, добором и удлинителями
- **WHEN** запрашивается каталог конфигураций и в базе есть door_configuration со ссылками на leaf_type, frame_type, door_casing_type и frame_extensions_type
- **THEN** в ответе для этой конфигурации присутствуют данные всех четырёх связанных типов

#### Scenario: Пустой каталог
- **WHEN** запрашивается каталог конфигураций и в базе нет ни одной строки door_configuration
- **THEN** эндпоинт возвращает пустой список с кодом 200, а не ошибку

#### Scenario: Полотно всегда включает данные своей коллекции
- **WHEN** запрашивается каталог конфигураций и в базе есть door_configuration со ссылкой на leaf_type
- **THEN** в ответе для leaf-компонента этой конфигурации присутствуют данные (id, code, name) collection, которой принадлежит этот leaf_type
