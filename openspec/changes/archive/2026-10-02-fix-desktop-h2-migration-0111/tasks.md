## 1. Принцип и миграции

- [x] 1.1 Записать в `CLAUDE.md` (раздел «Конвенции») принцип: в БД нет триггеров и процедурной логики, целостность — декларативными ограничениями
- [x] 1.2 Переписать `changes-desktop-overrides/0111-mirror-boolean-for-blind-leaf.yaml`: оставить `0111-1` и `0111-2` без изменений, `0111-3` удалить; в шапке — комментарий, почему пропущен
- [x] 1.3 Создать `changes/0114-mirror-finish-option-blind-leaf-fk.yaml`: changeSet для PostgreSQL (`dbms: postgresql`) — снять триггер и функцию `0111-3`; общий changeSet — `UNIQUE (id, panel_type)` на `leaf_type`, колонка `leaf_panel_type` + CHECK + составной FK в `mirror_finish_option`; rollback
- [x] 1.4 В `db.changelog-master.yaml`: include `0111` по контекстам `!desktop` / `desktop`, затем include `0114` без контекста

## 2. Проверка

- [x] 2.1 Прогнать `DesktopProfileTest` и `DesktopLauncherBackendStartupTest` (`--tests`) — оба проходят на H2, включая `0112`, `0113`, `0114`
- [x] 2.2 Прогнать `FurnitureCalculatorApplicationTests` на PostgreSQL — `0114` применяется; триггер и функция удалены, FK есть
- [x] 2.3 Проверить на обеих БД, что вставка `mirror_finish_option` для остеклённого `leaf_type` отклоняется, а для глухого проходит
- [x] 2.4 Выяснить, какие выпущенные десктопные версии затронуты, и сообщить пользователю для решения о перевыпуске
