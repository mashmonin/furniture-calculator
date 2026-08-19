## 1. JPA-сущности для справочников типов

- [x] 1.1 Создать `@Entity` для `leaf_type`, `frame_type`, `edge_type`, `door_casing_type`, `frame_extensions_type`, `liner_dimension_type`, `colour_type` (поля: id, code, name) в пакете `.../domain`
- [x] 1.2 Создать `@Entity` для `door_configuration` со связями `@ManyToOne` на `leaf_type` (обязательная) и опциональными `@ManyToOne` на `frame_type`, `edge_type`, `door_casing_type`, `frame_extensions_type`
- [x] 1.3 Создать `@Entity` для `liner_dimension_option` (связь на `liner_dimension_type`, значение, `is_standard`, опциональные владельцы `leaf_type`/`frame_type`/`edge_type`/`door_casing_type`/`frame_extensions_type`)
- [x] 1.4 Создать `@Entity` для `colour_option` (связь на `colour_type`, опциональные владельцы аналогично `liner_dimension_option`)
- [x] 1.5 Создать `@Entity` для `configuration_price` (retail_price, dealer_price, опциональные владельцы типа компонента, опциональные ссылки на length/height/thickness `liner_dimension_option` и на `colour_option`)

## 2. Репозитории

- [x] 2.1 `DoorConfigurationRepository extends JpaRepository<DoorConfiguration, Long>` с методом получения всех конфигураций (с `@EntityGraph` или fetch join на связанные типы, чтобы избежать N+1)
- [x] 2.2 `LinerDimensionOptionRepository` с методами выборки по каждому из владельцев типа компонента (`findByLeafTypeId`, `findByFrameTypeId`, `findByEdgeTypeId`, `findByDoorCasingTypeId`, `findByFrameExtensionsTypeId`)
- [x] 2.3 `ColourOptionRepository` с аналогичными методами выборки по владельцу типа компонента
- [x] 2.4 `ConfigurationPriceRepository` с методами выборки кандидатов по владельцу типа компонента и допустимым (выбранной опции ИЛИ NULL) ссылкам на length/height/thickness/colour

## 3. Каталог конфигураций (GET)

- [x] 3.1 Определить DTO ответа: `DoorConfigurationDto` (данные компонентов: leaf и опционально frame/edge/doorCasing/frameExtensions, каждый — код/название типа + списки допустимых `liner_dimension_option` и `colour_option`)
- [x] 3.2 Реализовать `DoorConfigurationCatalogService`, собирающий список `DoorConfigurationDto` из `DoorConfigurationRepository` + опций по каждому присутствующему типу компонента
- [x] 3.3 Реализовать `DoorConfigurationController` с `GET /api/door-configurations`, возвращающий список `DoorConfigurationDto` (пустой список, если конфигураций нет)

## 4. Расчёт стоимости изделия (POST)

- [x] 4.1 Определить DTO запроса: id конфигурации + выбор опций по присутствующим компонентам (для каждого — опциональные id length/height/thickness `liner_dimension_option` и id `colour_option`, только для тех осей, что применимы к типу компонента)
- [x] 4.2 Определить DTO ответа: итоговые retail/dealer суммы + разбивка по компонентам (найденная цена компонента либо отметка "цена не найдена")
- [x] 4.3 Реализовать валидацию: `door_configuration` с данным id существует (иначе 404), выбранные опции для каждого компонента не запрашиваются для отсутствующих в конфигурации компонентов
- [x] 4.4 Реализовать валидацию принадлежности: каждая переданная `liner_dimension_option`/`colour_option` действительно принадлежит типу компонента, для которого она передана (иначе 400)
- [x] 4.5 Реализовать подбор наиболее специфичной цены для одного компонента: из кандидатов (непустые ссылки совпадают с выбором клиента) выбрать строку с максимальным числом непустых ссылок; при отсутствии кандидатов или неоднозначности (несколько с одинаковым максимумом) — компонент считается некалькулируемым
- [x] 4.6 Реализовать `DoorConfigurationPricingService`, применяющий подбор цены (4.5) к каждому присутствующему в конфигурации компоненту и суммирующий найденные retail/dealer цены
- [x] 4.7 Добавить в `DoorConfigurationController` `POST /api/door-configurations/{id}/price`, возвращающий DTO ответа расчёта

## 5. Тесты

- [x] 5.1 Интеграционные тесты каталога (`@SpringBootTest` + `@AutoConfigureMockMvc` или `@DataJpaTest`, поднимая тестовую БД через существующий Liquibase changelog): конфигурация только с полотном, конфигурация со всеми компонентами, пустой каталог
- [x] 5.2 Интеграционные тесты расчёта стоимости: успешный расчёт по всем компонентам, наиболее специфичная цена побеждает менее специфичную, строка с несовпадающей осью не подходит, частичный расчёт при отсутствии цены компонента, неоднозначное совпадение исключает компонент, ни один компонент не оценён
- [x] 5.3 Тесты валидации: несуществующий id конфигурации → 404, опция чужого компонента → 400

## 6. Проверка

- [x] 6.1 `./gradlew test` проходит
- [x] 6.2 `./gradlew build` проходит
