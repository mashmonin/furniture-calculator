## MODIFIED Requirements

### Requirement: Структура признака реверса door_configuration
Door_configuration ДОЛЖЕН (SHALL) иметь булев признак is_reverse со значением по умолчанию false. Строка со значением is_reverse = true МОЖЕТ (MAY), как и обычная строка (is_reverse = false), не ссылаться на frame_type — реверс-исполнение больше не требует обязательного короба (прежнее ограничение снято этим change). Общее правило «door_casing_type/frame_extensions_type требуют заданного frame_type» (см. «Структура door_configuration») применяется одинаково к реверсивным и обычным строкам. Признак принадлежит конкретной конфигурации, а не типу короба или полотна — один и тот же frame_type и leaf_type могут участвовать как в обычных, так и в реверсивных строках door_configuration.

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
