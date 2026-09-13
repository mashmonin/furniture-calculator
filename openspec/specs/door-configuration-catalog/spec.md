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
Door_configuration ДОЛЖЕН (SHALL) иметь булев признак is_reverse со значением по умолчанию false. Строка со значением is_reverse = true МОЖЕТ (MAY), как и обычная строка (is_reverse = false), не ссылаться на frame_type — реверс-исполнение больше не требует обязательного короба. Общее правило «door_casing_type/frame_extensions_type требуют заданного frame_type» (см. «Структура door_configuration») применяется одинаково к реверсивным и обычным строкам. Признак принадлежит конкретной конфигурации, а не типу короба или полотна — один и тот же frame_type и leaf_type могут участвовать как в обычных, так и в реверсивных строках door_configuration.

#### Scenario: Реверс по умолчанию выключен
- **WHEN** строка door_configuration вставляется без явного значения is_reverse
- **THEN** строка сохраняется с is_reverse = false

#### Scenario: Реверс без короба недопустим
- **WHEN** строка door_configuration вставляется с is_reverse = true и без ссылки на frame_type
- **THEN** строка успешно сохраняется — прежнее ограничение «реверс без короба недопустим» снято этим change, реверс теперь ведёт себя как обычная конфигурация (is_reverse = false) в части обязательности короба

#### Scenario: Наличник для реверса без короба по-прежнему недопустим
- **WHEN** строка door_configuration вставляется с is_reverse = true, со ссылкой на door_casing_type, но без ссылки на frame_type
- **THEN** база данных отклоняет вставку — общее правило «наличник требует короба» действует независимо от is_reverse

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
Запись liner_dimension_option ДОЛЖНА (SHALL) хранить числовое значение размера (value) и признак того, что это стандартное (каталожное) значение (is_standard); оба поля обязательны. Запись МОЖЕТ (MAY) дополнительно хранить min_value — нижнюю границу диапазона родительского размера (например, высоты полотна), для которого эта опция допустима, и max_value — верхнюю границу такого диапазона. Отсутствие min_value означает открытую (неограниченную) нижнюю границу; отсутствие max_value означает, что верхней границей диапазона считается value (как исторически для кромки), либо, если ни min_value, ни max_value не заданы, что диапазон вообще не ограничен и проверка родительского размера для этой опции не выполняется. Комбинация владельца, liner_dimension_type, value, min_value и max_value ДОЛЖНА (SHALL) быть уникальной — то есть для одного владельца и одной оси размера значение value МОЖЕТ (MAY) повторяться в разных строках, только если у них различаются min_value и/или max_value (непересекающиеся диапазоны).

#### Scenario: liner_dimension_option требует value и is_standard
- **Когда** строка liner_dimension_option вставляется без value или без is_standard
- **То** база данных отклоняет вставку

#### Scenario: Значение не может повторяться для одного владельца и типа размера
- **Когда** для одного и того же leaf_type и одного и того же liner_dimension_type вставляется вторая строка liner_dimension_option с уже существующим value и такими же (в том числе оба NULL) min_value и max_value
- **То** база данных отклоняет вставку

#### Scenario: liner_dimension_option может не иметь min_value
- **Когда** строка liner_dimension_option вставляется без значения min_value
- **То** строка успешно сохраняется, а её диапазон считается открытым снизу (например, «до value» или «до max_value»)

#### Scenario: liner_dimension_option может задавать диапазон через min_value и value
- **Когда** строка liner_dimension_option вставляется со значениями min_value и value, где min_value меньше value (например, min_value 2150, value 2400), и без max_value
- **То** строка успешно сохраняется и описывает диапазон [min_value, value]

#### Scenario: liner_dimension_option может задавать диапазон через min_value и max_value отдельно от value
- **Когда** строка liner_dimension_option вставляется со значением value, отличным от границ диапазона, и явными min_value и max_value (например, value 2170, min_value 1900, max_value 2100)
- **То** строка успешно сохраняется и описывает диапазон [min_value, max_value], отдельный от физического значения value

#### Scenario: Один и тот же value может повторяться для одного владельца с разными непересекающимися диапазонами
- **Когда** для одного и того же frame_type и одного и того же liner_dimension_type вставляются две строки liner_dimension_option с одинаковым value, но разными парами (min_value, max_value), не пересекающимися между собой (например, [2150, 2250] и [2300, 2300])
- **То** обе строки успешно сохраняются

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

### Requirement: Структура mirror_finish_type
Mirror_finish_type ДОЛЖЕН (SHALL) хранить наименование исполнения зеркала (name) и процент наценки к цене полотна (surcharge_percent); оба поля обязательны. Mirror_finish_type МОЖЕТ (MAY) дополнительно хранить краткое наименование (short_name) — необязательное человекочитаемое имя, короче name. Mirror_finish_type — глобальный справочник, не привязанный к конкретному leaf_type или коллекции.

Для следующих строк mirror_finish_type ДОЛЖНО (SHALL) быть задано указанное краткое наименование:
- «Глухое плоское полотно с зеркалом с одной стороны» → «Базовое»
- «Глухое плоское полотно с зеркалом с фацетом с одной стороны» → «С фацетом»
- «Глухое плоское полотно с зеркалом серое/бронза с одной стороны» → «Серое/бронза»

Для остальных строк mirror_finish_type краткое наименование не задаётся этим изменением.

#### Scenario: Правило требует name и surcharge_percent
- **WHEN** строка mirror_finish_type вставляется без name или без surcharge_percent
- **THEN** база данных отклоняет вставку

#### Scenario: Существует несколько исполнений с разными процентами
- **WHEN** в mirror_finish_type есть несколько строк с разными surcharge_percent
- **THEN** все строки сохраняются и доступны независимо друг от друга

#### Scenario: Краткое наименование необязательно
- **WHEN** строка mirror_finish_type вставляется без short_name
- **THEN** строка сохраняется успешно, name и surcharge_percent не затронуты

#### Scenario: Заданы краткие наименования трёх базовых исполнений
- **WHEN** запрашивается mirror_finish_type «Глухое плоское полотно с зеркалом с одной стороны», «...с фацетом с одной стороны» или «...серое/бронза с одной стороны»
- **THEN** их краткие наименования равны «Базовое», «С фацетом» и «Серое/бронза» соответственно

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
Для leaf-компонента каждой door_configuration в ответе каталога конфигураций система ДОЛЖНА (SHALL) включать список исполнений зеркала, допустимых для leaf_type этой конфигурации (определяется наличием строки mirror_finish_option для пары этот leaf_type + исполнение) — id (mirror_finish_type.id — тот же id, что используется в запросе расчёта и в ответе эндпоинта получения процентов надбавок), наименование и краткое наименование (значение short_name соответствующего mirror_finish_type, либо `null`, если оно не задано). Для компонентов, отличных от leaf (frame, edge, doorCasing, frameExtensions), список исполнений зеркала ДОЛЖЕН (SHALL) быть пустым.

#### Scenario: Полотно из подходящей коллекции показывает допустимые исполнения зеркала
- **WHEN** запрашивается каталог конфигураций и leaf_type данной door_configuration имеет строки mirror_finish_option
- **THEN** в ответе для leaf-компонента этой конфигурации присутствует список этих исполнений зеркала, включая краткое наименование каждого (или `null`, если оно не задано)

#### Scenario: Полотно без исполнений зеркала возвращает пустой список
- **WHEN** запрашивается каталог конфигураций и leaf_type данной door_configuration не имеет ни одной строки mirror_finish_option
- **THEN** в ответе для leaf-компонента этой конфигурации список исполнений зеркала пуст, запрос не завершается ошибкой

#### Scenario: Остальные компоненты не имеют исполнений зеркала
- **WHEN** запрашивается каталог конфигураций
- **THEN** для frame-, edge-, doorCasing- и frameExtensions-компонентов список исполнений зеркала в ответе всегда пуст

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
