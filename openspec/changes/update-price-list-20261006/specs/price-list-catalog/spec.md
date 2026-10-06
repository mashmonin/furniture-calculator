## MODIFIED Requirements

### Requirement: Прайс-лист «Эмаль лайт»
В справочнике price_list ДОЛЖНА (SHALL) существовать запись `PL-002` с name `hausdoors_emal_layt_tsfo_06_10_2026`. Коллекции MONO, QUADRO, VENEZIA и REFLEX ДОЛЖНЫ (SHALL) ссылаться на этот прайс-лист; остальные коллекции продолжают ссылаться на `PL-001`.

#### Scenario: Прайс-лист «Эмаль лайт» заведён
- **WHEN** запрашивается справочник прайс-листов после применения миграций
- **THEN** в нём есть запись `PL-002` с name `hausdoors_emal_layt_tsfo_06_10_2026`

#### Scenario: Новые коллекции принадлежат PL-002
- **WHEN** запрашиваются коллекции MONO, QUADRO, VENEZIA и REFLEX
- **THEN** каждая из них ссылается на price_list `PL-002`
