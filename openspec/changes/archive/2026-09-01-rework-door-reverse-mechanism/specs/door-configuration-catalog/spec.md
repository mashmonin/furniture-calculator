## REMOVED Requirements

### Requirement: Реверсивность frame_type
**Reason**: Признак реверса переезжает с типа короба (frame_type) на конкретную строку конфигурации (door_configuration) — см. добавленное требование «Структура признака реверса door_configuration». Короб «КОМПЛАНАР РЕВЕРС» (frame_type с этим флагом) упраздняется как отдельная сущность: реверс-исполнение теперь выражается обычным коробом КОМПЛАНАР с признаком реверса на самой конфигурации, а не отдельным кодом frame_type.
**Migration**: Существующие строки door_configuration, ссылавшиеся на реверсивный frame_type, пересозданы под обычным frame_type КОМПЛАНАР с `is_reverse = true`; сам реверсивный frame_type и его frame_post удалены из справочника.

## MODIFIED Requirements

### Requirement: Уникальность door_configuration
Комбинация leaf_type, frame_type, edge_type, door_casing_type, frame_extensions_type и is_reverse в door_configuration ДОЛЖНА (SHALL) быть уникальной — включая случаи, когда один или несколько из необязательных типов не заданы.

#### Scenario: Повторная конфигурация отклоняется
- **Когда** вставляется вторая строка door_configuration с точно такой же комбинацией leaf_type/frame_type/edge_type/door_casing_type/frame_extensions_type/is_reverse (в том числе если оба раза какой-то из необязательных типов не задан)
- **То** база данных отклоняет вставку

#### Scenario: Обычная и реверсивная конфигурации с одинаковыми ссылками не считаются дублем
- **Когда** вставляются две строки door_configuration с одинаковыми leaf_type/frame_type/edge_type/door_casing_type/frame_extensions_type, но разным значением is_reverse
- **То** обе строки успешно сохраняются

## ADDED Requirements

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
