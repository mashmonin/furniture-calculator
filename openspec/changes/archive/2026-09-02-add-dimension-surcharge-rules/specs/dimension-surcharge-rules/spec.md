## Purpose

Определяет справочник бизнес-правил фабрики о наценках за конкретные нестандартные значения размеров компонентов — отдельно от каталога допустимых конфигураций (door-configuration-catalog), который описывает лишь то, какие сочетания и стандартные размеры вообще существуют.

## ADDED Requirements

### Requirement: Структура dimension_surcharge_rule
Dimension_surcharge_rule ДОЛЖЕН (SHALL) ссылаться ровно на один liner_dimension_type и хранить числовое значение размера (value) в миллиметрах и процент наценки (surcharge_percent); оба поля обязательны. Комбинация liner_dimension_type и value ДОЛЖНА (SHALL) быть уникальной.

#### Scenario: Правило требует liner_dimension_type, value и surcharge_percent
- **WHEN** строка dimension_surcharge_rule вставляется без ссылки на liner_dimension_type, без value или без surcharge_percent
- **THEN** база данных отклоняет вставку

#### Scenario: Значение не может повторяться для одного типа размера
- **WHEN** для одного и того же liner_dimension_type вставляется вторая строка dimension_surcharge_rule с уже существующим value
- **THEN** база данных отклоняет вставку

#### Scenario: Для одного типа размера может быть несколько правил с разными значениями
- **WHEN** для одного liner_dimension_type вставляются несколько строк dimension_surcharge_rule с разными value и разными surcharge_percent
- **THEN** все строки сохраняются

### Requirement: Независимость от каталога допустимых конфигураций
Dimension_surcharge_rule НЕ ДОЛЖЕН (SHALL NOT) ссылаться на конкретный leaf_type, frame_type, edge_type, door_casing_type, frame_extensions_type или door_configuration — правило зависит только от типа размера (liner_dimension_type) и конкретного значения, одинаково для любого владельца этого типа размера.

#### Scenario: Одно правило применимо ко всем компонентам данного типа размера
- **WHEN** существует dimension_surcharge_rule для liner_dimension_type с кодом «ДЛИНА» и значения 950
- **THEN** это правило одинаково применимо к любому компоненту, для которого допустима длина (независимо от конкретной модели полотна)
