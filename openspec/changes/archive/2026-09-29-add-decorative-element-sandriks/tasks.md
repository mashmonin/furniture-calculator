## 1. Схема и данные

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0110-decorative-element-sandriks.yaml`: changeset `createTable` для `decorative_element_width_option` (id, `decorative_element_type_id` FK, `min_value` DECIMAL nullable, `max_value` DECIMAL nullable, `width_mm` DECIMAL not null)
- [x] 1.2 В том же файле — changeset вставки категории «Сандрики» (`DEC-003`) и трёх decorative_element_type («Сандрик Левия» — 200/67, «Сандрик Арно» — 188/64,5, «Сандрик Адела» — 93/59; `width_mm` = NULL у всех трёх; цены — дилер/розница 9574/16755, 6237/10915, 5237/9166)
- [x] 1.3 В том же файле — changeset вставки 9 строк `decorative_element_width_option` (по 3 диапазона на тип, см. specs/decorative-element-catalog/spec.md)
- [x] 1.4 В том же файле — changeset вставки `decorative_element_option` для всех трёх типов × всех leaf_type коллекций «Элегант» (`LC-003`) и «Гармония» (`LC-004`)
- [x] 1.5 Подключить `0110-decorative-element-sandriks.yaml` в `db.changelog-master.yaml`

## 2. Backend — модель и резолв ширины

- [x] 2.1 Создать domain-класс `DecorativeElementWidthOption` (id, ManyToOne на `DecorativeElementType`, `minValue`, `maxValue`, `widthMm`) и репозиторий `DecorativeElementWidthOptionRepository` с методом поиска строк по `decorativeElementTypeId`
- [x] 2.2 В `DoorConfigurationPricingService` (метод построения `DecorativeElementPriceDto` из выбранных позиций) добавить резолв эффективной ширины: если у типа есть строки `decorative_element_width_option` — искать среди них диапазон, содержащий переданную длину полотна (включительно с обеих сторон, отсутствующая граница — без ограничения), и использовать её `width_mm`; при отсутствии совпадения или отсутствии длины полотна — ширина не определена (null); если строк диапазонов нет — использовать `type.getWidthMm()` как раньше
- [x] 2.3 Прокинуть резолвленную длину полотна в это построение декоративных элементов из всех трёх точек вызова: расчёт по `door_configuration` (уже резолвленная длина leaf-компонента), расчёт отдельного полотна `leaf-standalone-pricing` (уже резолвленная длина полотна), выгрузка спецификации/заказа (`resolveSpecificationComponents`, использует ту же длину, что и для leaf-компонента)

## 3. Backend — независимый эндпоинт decorative-elements-price

- [x] 3.1 Добавить необязательное поле `leafLengthMm` (`BigDecimal`) в `DecorativeElementPricingRequestDto`
- [x] 3.2 В `DoorConfigurationPricingService.calculateDecorativeElements` передать `leafLengthMm` из запроса в резолв ширины (см. 2.2)

## 4. Frontend

- [x] 4.1 В `ConfiguratorScreen.tsx`, в месте формирования запроса независимого этапа расчёта декоративных элементов (`calculateDecorativeElementsPrice`), добавить передачу текущей длины выбранного полотна (то же значение, что уже используется для этапа «Полотно»)
- [x] 4.2 Добавить длину полотна в зависимости эффекта, запускающего расчёт этого этапа (чтобы изменение длины полотна перезапускало расчёт декоративных элементов — см. specs/door-configurator-ui/spec.md, «Изменение длины полотна перезапускает расчёт декоративных элементов»)

## 5. Проверка

- [x] 5.1 Запустить backend локально, проверить применение миграции без ошибок
- [x] 5.2 Через `POST /api/decorative-elements/price` с `leafLengthMm: 700` и позицией «Сандрик Левия» убедиться, что в разбивке ширина равна 1150; без `leafLengthMm` — ширина не определена, ошибки нет
- [x] 5.3 В конфигураторе (`npm run dev`) выбрать полотно коллекции «Элегант»/«Гармония», добавить сандрик — убедиться, что расчёт проходит; изменить длину полотна и убедиться, что этап «Декоративные элементы» пересчитывается
- [x] 5.4 Проверить выгрузку спецификации с позицией сандрика — убедиться, что колонка размеров показывает `длина × ширина × толщина` с резолвленной шириной
