## 1. Liquibase-миграции

- [x] 1.1 Новый changeset: добавить nullable-колонку `short_name VARCHAR` в таблицы `edge_type`, `door_casing_type`, `frame_extensions_type`, `post_type`, `frame_type`, `hardware_category`, `hardware_type`, `liner_dimension_type`, `colour_type`, `leaf_type`, `leaf_collection`
- [x] 1.2 Новый changeset: добавить nullable-колонку `short_name VARCHAR` в таблицу `mirror_finish_type`
- [x] 1.3 Новый changeset: обновить `short_name` для строк `edge_type` по их `code` — `ET-001` → «Прямая», `ET-002` → «С четвертью»
- [x] 1.4 Новый changeset: обновить `short_name` для строк `mirror_finish_type` по их `name` — «Глухое плоское полотно с зеркалом с одной стороны» → «Базовое», «...с фацетом с одной стороны» → «С фацетом», «...серое/бронза с одной стороны» → «Серое/бронза»
- [x] 1.5 Подключить новые changeset-файлы в `db.changelog-master.yaml` в правильном порядке (после уже существующих)

## 2. Backend: доменная модель и общий контракт CatalogType

- [x] 2.1 Добавить `String getShortName();` в интерфейс `CatalogType`
- [x] 2.2 Добавить поле `shortName` (nullable `@Column(name = "short_name")`) и геттер в каждый из 11 классов: `EdgeType`, `DoorCasingType`, `FrameExtensionsType`, `PostType`, `FrameType`, `HardwareCategory`, `HardwareType`, `LinerDimensionType`, `ColourType`, `LeafType`, `LeafCollection`
- [x] 2.3 Добавить поле `shortName` в `ReferenceDto` (`id`, `code`, `name`, `shortName`) и обновить `ReferenceDto.from(CatalogType)`, передав `type.getShortName()`
- [x] 2.4 Проверить все места ручного конструирования `new ReferenceDto(...)` (в частности `DoorConfigurationCatalogService.toDto(MirrorFinishOption)`) и обновить под новую сигнатуру, передав `option.getMirrorFinishType().getShortName()`

## 3. Backend: MirrorFinishType и связанные DTO

- [x] 3.1 Добавить поле `shortName` (nullable `@Column(name = "short_name")`) и геттер в `MirrorFinishType`
- [x] 3.2 Добавить поле `shortName` в `MirrorFinishSurchargeDto` (`id`, `name`, `shortName`, `surchargePercent`)
- [x] 3.3 Обновить `PricingSurchargesService.getPricingSurcharges()`, чтобы передавать `type.getShortName()` в `MirrorFinishSurchargeDto`

## 4. Frontend: типы и отображение

- [x] 4.1 Добавить `shortName: string | null` в интерфейс `ReferenceDto` (`frontend/src/api/types.ts`)
- [x] 4.2 Добавить `shortName: string | null` в интерфейс `MirrorFinishSurchargeDto` (`frontend/src/api/types.ts`)
- [x] 4.3 В местах выбора кромки (edge type) и исполнения зеркала (mirror finish) в `App.tsx` отображать `shortName`, если он задан, иначе — `name` (без изменений для остальных справочников на этом этапе)

## 5. Проверка

- [x] 5.1 Backend: точечные тесты (`./gradlew test --tests "*<конкретный класс>*"`, не полный прогон) на новые поля `ReferenceDto`/`MirrorFinishSurchargeDto` и на эндпоинты, где они участвуют — `PricingSurchargesServiceTest` проходит; `DoorConfigurationCatalogServiceTest` падает на 5 тестах из-за отсутствующего мока `MirrorFinishOptionRepository`, это предсуществующая проблема (воспроизводится и на HEAD без этого изменения), не связанная с short_name
- [x] 5.2 Frontend: `npm run build` (`tsc -b && vite build`) для проверки типов
- [x] 5.3 Backend проверен вручную по API (докер-Postgres уже был поднят; временный `./gradlew bootRun --args='--server.port=8099'`, чтобы не мешать уже запущенному в IntelliJ инстансу на 8080): `GET /api/pricing-surcharges` отдаёт shortName «Базовое»/«С фацетом»/«Серое/бронза» для трёх исполнений зеркала, `GET /api/door-configurations` отдаёт shortName «Прямая»/«С четвертью» для edge_type и корректно возвращает shortName для исполнений зеркала полотна; для остальных справочников (например leaf_type) shortName корректно `null`, остальные поля не затронуты. Временный инстанс остановлен, `vite.config.ts` возвращён к исходному прокси. Визуальную проверку в браузере выполнить не удалось — расширение Claude in Chrome не подключено в этой сессии; отображение кратких наименований в `App.tsx` (task 4.3) проверено только по коду и типам, вручную в браузере не проверялось — рекомендуется проверить визуально отдельно
