## Purpose

Определяет справочник типов позиций короба (post_type) и структуру frame_post — из каких стоек или комплектов стоек состоит конкретный короб (frame_type) и сколько стоит каждая такая позиция.

## Requirements

### Requirement: Структура post_type
Post_type ДОЛЖЕН (SHALL) иметь непустое человекочитаемое название (name) и непустой код (code), уникальный в пределах справочника post_type.

#### Scenario: Post_type требует name и code
- **WHEN** строка post_type вставляется без name или без code
- **THEN** база данных отклоняет вставку

#### Scenario: Code post_type уникален
- **WHEN** вставляется вторая строка post_type с уже существующим code
- **THEN** база данных отклоняет вставку

### Requirement: Структура frame_post
Frame_post ДОЛЖЕН (SHALL) ссылаться ровно на один frame_type (владелец) и ровно один post_type. Каждая запись ДОЛЖНА (SHALL) хранить quantity (целое число физических стоек, которые представляет эта позиция) и обязательные retail_price и dealer_price; length (единственное пока измерение стойки) необязательна.

#### Scenario: Frame_post требует существующий frame_type и post_type
- **WHEN** строка frame_post вставляется без ссылки на frame_type или post_type, либо со ссылкой на несуществующую запись любого из этих справочников
- **THEN** база данных отклоняет вставку

#### Scenario: Frame_post требует quantity, retail_price и dealer_price
- **WHEN** строка frame_post вставляется без quantity, без retail_price или без dealer_price
- **THEN** база данных отклоняет вставку

#### Scenario: Frame_post может не иметь length
- **WHEN** строка frame_post вставляется без значения length
- **THEN** строка успешно сохраняется

### Requirement: Состав короба из записей frame_post
Один frame_type МОЖЕТ (MAY) иметь несколько записей frame_post с разными post_type (например, отдельно незарезная стойка и отдельно комплект зарезных стоек), либо ровно одну запись frame_post, представляющую короб целиком одним комплектом. Комбинация frame_type и post_type в frame_post ДОЛЖНА (SHALL) быть уникальной.

#### Scenario: Короб из нескольких отдельных позиций
- **WHEN** для одного frame_type вставляются две записи frame_post с разными post_type (например, «Незарезная стойка» и «Комплект зарезных стоек»)
- **THEN** обе записи сохраняются и обе принадлежат этому frame_type

#### Scenario: Короб одним комплектом
- **WHEN** для одного frame_type вставляется ровно одна запись frame_post
- **THEN** эта запись считается ценой короба целиком

#### Scenario: Повторная позиция того же типа для короба отклоняется
- **WHEN** для frame_type, у которого уже есть запись frame_post с определённым post_type, вставляется вторая запись frame_post с тем же post_type
- **THEN** база данных отклоняет вставку
