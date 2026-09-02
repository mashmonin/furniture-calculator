## ADDED Requirements

### Requirement: Структура mirror_finish_type
Mirror_finish_type ДОЛЖЕН (SHALL) хранить наименование исполнения зеркала (name) и процент наценки к цене полотна (surcharge_percent); оба поля обязательны. Mirror_finish_type — глобальный справочник, не привязанный к конкретному leaf_type или коллекции.

#### Scenario: Правило требует name и surcharge_percent
- **WHEN** строка mirror_finish_type вставляется без name или без surcharge_percent
- **THEN** база данных отклоняет вставку

#### Scenario: Существует несколько исполнений с разными процентами
- **WHEN** в mirror_finish_type есть несколько строк с разными surcharge_percent
- **THEN** все строки сохраняются и доступны независимо друг от друга

### Requirement: Владение mirror_finish_option
Mirror_finish_option ДОЛЖЕН (SHALL) ссылаться ровно на один mirror_finish_type и ровно один leaf_type — какое исполнение зеркала допустимо для какой модели полотна. Комбинация mirror_finish_type и leaf_type ДОЛЖНА (SHALL) быть уникальной. В отличие от liner_dimension_option/colour_option, mirror_finish_option ДОЛЖЕН (SHALL) владеться исключительно leaf_type — ссылки на frame_type/edge_type/door_casing_type/frame_extensions_type не предусмотрены (исполнение зеркала — свойство полотна).

#### Scenario: Запись mirror_finish_option требует leaf_type и mirror_finish_type
- **WHEN** строка mirror_finish_option вставляется без ссылки на leaf_type или без ссылки на mirror_finish_type
- **THEN** база данных отклоняет вставку

#### Scenario: Исполнение не может повторяться для одного полотна
- **WHEN** для одного и того же leaf_type вставляется вторая строка mirror_finish_option с уже существующим mirror_finish_type
- **THEN** база данных отклоняет вставку

#### Scenario: Одно и то же исполнение допустимо для нескольких моделей полотна
- **WHEN** для одного mirror_finish_type вставляются строки mirror_finish_option для нескольких разных leaf_type
- **THEN** все строки сохраняются независимо

### Requirement: Допустимые исполнения зеркала в каталоге конфигураций
Для leaf-компонента каждой door_configuration в ответе каталога конфигураций система ДОЛЖНА (SHALL) включать список исполнений зеркала, допустимых для leaf_type этой конфигурации (определяется наличием строки mirror_finish_option для пары этот leaf_type + исполнение) — id (mirror_finish_type.id — тот же id, что используется в запросе расчёта и в ответе эндпоинта получения процентов надбавок) и наименование. Для компонентов, отличных от leaf (frame, edge, doorCasing, frameExtensions), список исполнений зеркала ДОЛЖЕН (SHALL) быть пустым.

#### Scenario: Полотно из подходящей коллекции показывает допустимые исполнения зеркала
- **WHEN** запрашивается каталог конфигураций и leaf_type данной door_configuration имеет строки mirror_finish_option
- **THEN** в ответе для leaf-компонента этой конфигурации присутствует список этих исполнений зеркала

#### Scenario: Полотно без исполнений зеркала возвращает пустой список
- **WHEN** запрашивается каталог конфигураций и leaf_type данной door_configuration не имеет ни одной строки mirror_finish_option
- **THEN** в ответе для leaf-компонента этой конфигурации список исполнений зеркала пуст, запрос не завершается ошибкой

#### Scenario: Остальные компоненты не имеют исполнений зеркала
- **WHEN** запрашивается каталог конфигураций
- **THEN** для frame-, edge-, doorCasing- и frameExtensions-компонентов список исполнений зеркала в ответе всегда пуст
