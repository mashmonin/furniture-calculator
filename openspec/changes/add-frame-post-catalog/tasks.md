## 1. Liquibase: справочник post_type и таблица frame_post

- [x] 1.1 Новый changeset-файл `0022-frame-post-catalog.yaml`: `createTable: post_type` (id) + `addColumn` (name VARCHAR NOT NULL, code VARCHAR NOT NULL UNIQUE) — по образцу двухшаговой миграции существующих справочников
- [x] 1.2 Changeset: `createTable: frame_post` — id; `frame_type_id` (BIGINT NOT NULL, inline FK на `frame_type(id)`); `post_type_id` (BIGINT NOT NULL, inline FK на `post_type(id)`); `quantity` (INT NOT NULL); `length` (DECIMAL, nullable); `retail_price` (DECIMAL NOT NULL); `dealer_price` (DECIMAL NOT NULL)
- [x] 1.3 Changeset: уникальный индекс на `frame_post (frame_type_id, post_type_id)` (по образцу `uk_liner_dimension_option_leaf_type` — `sql`/`CREATE UNIQUE INDEX`)
- [x] 1.4 Changeset: `insert` 2 строк post_type — «Незарезная стойка» (код `PT-001`), «Комплект зарезных стоек» (код `PT-002`)
- [x] 1.5 Changeset: `insert` 2 строк frame_post для КОМПЛАНАР (`frame_type.code = 'FT-002'`) — «Незарезная стойка»: quantity 1, length не задана, dealer_price 2027, retail_price 3549; «Комплект зарезных стоек»: quantity 2, length 2170, dealer_price 4027, retail_price 7047 (значения frame_type_id/post_type_id — через подзапрос по code, аналогично `0018-atmosfera-leaf-prices.yaml`)
- [x] 1.6 Подключить `0022-frame-post-catalog.yaml` в `db.changelog-master.yaml`; поднять `docker compose up -d`, прогнать миграцию, проверить в БД, что для КОМПЛАНАР (FT-002) есть ровно 2 строки frame_post с ожидаемыми ценами

## 2. Backend: сущности, репозиторий, расчёт стоимости

- [x] 2.1 Новая JPA-сущность `PostType` (`@Entity @Table(name = "post_type")`) в `domain/`, реализует `CatalogType`, по образцу `LeafType`
- [x] 2.2 Новая JPA-сущность `FramePost` (`@Entity @Table(name = "frame_post")`) в `domain/` — `@ManyToOne` на `FrameType` (`frame_type_id`, NOT NULL) и `PostType` (`post_type_id`, NOT NULL), поля `quantity` (Integer), `length` (BigDecimal, nullable), `retailPrice`, `dealerPrice` (BigDecimal, NOT NULL) — по образцу `ConfigurationPrice`
- [x] 2.3 Новый репозиторий `FramePostRepository extends JpaRepository<FramePost, Long>` с методом `findByFrameTypeId(Long frameTypeId)`
- [x] 2.4 В `DoorConfigurationPricingService`: для frame-компонента (`FrameType`) заменить путь через `findMostSpecificPrice`/`configurationPriceRepository` на суммирование `retailPrice`/`dealerPrice` всех `FramePost` этого `frameTypeId` (через `FramePostRepository`); если записей нет — компонент остаётся некалькулируемым (`priced = false`), как и сегодня; для остальных компонентов (`leaf`/`edge`/`doorCasing`/`frameExtensions`) поведение не меняется
- [x] 2.5 Обновить/добавить backend-тесты: юнит-тест `DoorConfigurationPricingServiceTest` (или аналогичный) на суммирование цен нескольких `frame_post` и на некалькулируемый короб без записей; интеграционный тест на `POST /api/door-configurations/{id}/price` — короб с двумя `frame_post`-записями суммируется в итоговую стоимость

## 3. Проверка

- [x] 3.1 Backend: `./gradlew test`
- [x] 3.2 Ручная проверка в браузере (`docker compose up -d`, backend перезапущен с новым кодом, `npm run dev`): выбрать коллекцию → полотно → короб КОМПЛАНАР → далее по каскаду → рассчитать стоимость → убедиться, что короб теперь показывает цену 3549 / 2027 + 7047 / 4027 (розница/дилер), а не «цена не найдена»

## 4. Backend: список позиций короба в каталоге

- [x] 4.1 Новый DTO `FramePostDto` (`postType: ReferenceDto`, `quantity`, `length` (nullable), `retailPrice`, `dealerPrice`) в `dto/`
- [x] 4.2 Расширить `ComponentCatalogDto` полем `posts: List<FramePostDto>` — пустой список для всех компонентов, кроме frame
- [x] 4.3 В `DoorConfigurationCatalogService` добавить `buildFrameComponent(FrameType)` (по образцу `buildLeafComponent`) — заполняет `posts` через `FramePostRepository.findByFrameTypeId`, маппит каждую `FramePost` в `FramePostDto` (`ReferenceDto.from(post.getPostType())` для postType); использовать этот метод вместо общего `buildComponent` для frame в `toDto`
- [x] 4.4 Обновить/добавить backend-тесты: `DoorConfigurationCatalogServiceTest` — frame с двумя `frame_post` возвращает обе позиции в `posts`, frame без `frame_post` возвращает пустой список; `DoorConfigurationApiIntegrationTest` — `GET /api/door-configurations` отдаёт заполненный `posts` для короба с записями frame_post

## 5. Frontend: отображение позиций короба

- [x] 5.1 Обновить типы каталога (`frontend/src/api/types.ts`) — добавить `FramePostDto` (postType: `ReferenceDto`, quantity, length, retailPrice, dealerPrice) и поле `posts: FramePostDto[]` в `ComponentCatalogDto`
- [x] 5.2 В `App.tsx`, в карточке компонента на шаге «2. Выберите опции»: если у компонента непустой `posts`, отобразить список его позиций (название post_type, quantity, length если задана, retailPrice/dealerPrice) через компонент antd (`List` уже импортирован); при пустом `posts` — без изменений в текущем виде карточки
- [x] 5.3 Frontend: `npm run lint` и `npm run build`; ручная проверка в браузере — карточка «Короб» для конфигурации с КОМПЛАНАР показывает обе позиции («Незарезная стойка», «Комплект зарезных стоек») с их данными
