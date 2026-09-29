## 1. Данные каталога

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0108-decorative-elements-block-base.yaml` по образцу `0107-decorative-elements-plinth.yaml`: changeset вставки decorative_element_category «Блок и база» (код `DEC-002`)
- [x] 1.2 В том же файле — changeset вставки пяти decorative_element_type (коды `DET-003`…`DET-007`) со значениями name/length_mm/dealer_price/retail_price из specs/decorative-element-catalog/spec.md
- [x] 1.3 В том же файле — changeset вставки decorative_element_option для всех пяти типов × всех leaf_type коллекций «Элегант» (`LC-003`) и «Гармония» (`LC-004`)
- [x] 1.4 Подключить `0108-decorative-elements-block-base.yaml` в `db.changelog-master.yaml`

## 2. Проверка

- [x] 2.1 Запустить приложение локально (`docker compose up -d`, `./gradlew bootRun`) и проверить эндпоинт каталога декоративных элементов — новая категория и пять типов присутствуют с верными длиной и ценами
- [x] 2.2 В конфигураторе (`npm run dev`) выбрать полотно коллекции «Элегант» и «Гармония» — блок «Декоративные элементы» показывает все пять новых позиций; для полотна другой коллекции (например, «Вертикаль» или «Фантом») — не показывает
