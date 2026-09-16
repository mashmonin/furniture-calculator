## 1. Типы и API-клиент

- [x] 1.1 В `frontend/src/api/types.ts` добавить `edgeTypeId?: number` в `PricingRequestDto` (верхний уровень, рядом с `isReverse`, не внутри `ComponentSelectionDto`).
- [x] 1.2 В `frontend/src/api/types.ts` добавить `FrameGroupPricingRequestDto` (`frame?: ComponentSelectionDto`, `doorCasingTypeId?: number`, `doorCasing?: ComponentSelectionDto`, `frameExtensionsTypeId?: number`, `frameExtensions?: ComponentSelectionDto`, `leafHeightValue?: number`) и `FrameGroupPricingResponseDto` (`totalRetailPrice`, `totalDealerPrice`, `components: ComponentPriceDto[]`).
- [x] 1.3 В `frontend/src/api/types.ts` добавить `HardwarePricingRequestDto` (`hardware?: HardwareSelectionDto[]`) и `HardwarePricingResponseDto` (`totalRetailPrice`, `totalDealerPrice`, `hardware: HardwarePriceDto[]`).
- [x] 1.4 В `frontend/src/api/doorConfigurations.ts` добавить `calculateFrameGroupPrice(frameTypeId: number, request: FrameGroupPricingRequestDto): Promise<FrameGroupPricingResponseDto>` (`POST /api/frame-types/${frameTypeId}/price`) и `calculateHardwarePrice(request: HardwarePricingRequestDto): Promise<HardwarePricingResponseDto>` (`POST /api/hardware/price`), по образцу существующего `calculateLeafPrice`.
- [x] 1.5 Удалить `calculatePrice` из `frontend/src/api/doorConfigurations.ts` — предварительно проверить поиском по репозиторию (`grep -rn "calculatePrice"` в `frontend/src`), что она используется только в `App.tsx`, и убрать импорт там же (задача 3.x).

## 2. Именованные id короба/наличника/добора

- [x] 2.1 В `App.tsx`, рядом с уже существующими `frameComponent`/`doorCasingComponent`/`frameExtensionsComponent` (вычисляемыми из `resolvedComponent` соответствующих шагов каскада), добавить именованные `frameTypeId = frameComponent?.type.id`, `doorCasingTypeId = doorCasingComponent?.type.id`, `frameExtensionsTypeId = frameExtensionsComponent?.type.id` (по образцу существующего `leafTypeId`).

## 3. Три независимых состояния и debounce-эффекта

- [x] 3.1 Заменить единственную тройку `pricingResult`/`pricingLoading`/`pricingError` и единственный `requestSeqRef` на три независимых набора: `leafPricingResult/Loading/Error` + свой `requestSeqRef`, `frameGroupPricingResult/Loading/Error` + свой `requestSeqRef`, `hardwarePricingResult/Loading/Error` + свой `requestSeqRef`.
- [x] 3.2 Написать debounce-эффект этапа «Полотно»: условие запуска `leafTypeId !== undefined`; собирает `leafSelection` (как сейчас, включая `mirrorFinishTypeId`/`glazingTypeId`) и, если определён вид кромки, добавляет `edgeTypeId` и опции кромки (`selection.edge`); вызывает `calculateLeafPrice`; НЕ включает фурнитуру в запрос (фурнитура — отдельный этап, задача 3.4).
- [x] 3.3 Написать debounce-эффект этапа «Короб и обрамление»: условие запуска `frameTypeId !== undefined`; собирает `FrameGroupPricingRequestDto` из `selection.frame`/`selection.doorCasing`/`selection.frameExtensions`, `doorCasingTypeId`/`frameExtensionsTypeId` (если определены) и `leafHeightValue`; вызывает `calculateFrameGroupPrice(frameTypeId, request)`.
- [x] 3.4 Написать debounce-эффект этапа «Фурнитура»: условие запуска `leafTypeId !== undefined` И есть хотя бы одна завершённая позиция фурнитуры (`hardwareOptionId !== undefined`); собирает `HardwarePricingRequestDto` из `hardwareLines`; вызывает `calculateHardwarePrice`.
- [x] 3.5 Убедиться, что зависимости каждого `useEffect` соответствуют только тем полям состояния, что реально влияют на его запрос (полотно+кромка не зависит от `hardwareLines`; короб/обрамление не зависит от `hardwareLines`/`mirrorFinishTypeId`/`glazingTypeId`; фурнитура не зависит от `selection.leaf`/`selection.frame`/`mirrorFinishTypeId`/`glazingTypeId`).

## 4. Объединение результатов для sticky-панели

- [x] 4.1 Написать функцию/вычисляемое значение, определяющее «применимость» каждого этапа (leaf: `leafTypeId !== undefined`; frame group: `frameTypeId !== undefined`; hardware: `leafTypeId !== undefined` и есть завершённая позиция).
- [x] 4.2 Написать объединение: если у любого применимого этапа есть ошибка — единое сообщение об ошибке; иначе если любой применимый этап ещё не завершён — состояние ожидания; иначе — объединённый результат: сумма `totalRetailPrice`/`totalDealerPrice` применимых этапов, `components` — конкатенация `leafResult.components` и `frameGroupResult.components`, отсортированная по существующему `COMPONENT_ORDER`, `hardware` — из `hardwareResult.hardware` (пусто, если этап фурнитуры неприменим).
- [x] 4.3 Заменить в JSX sticky-панели (~1559-1656) использование `pricingResult`/`pricingLoading`/`pricingError` на производные значения из задачи 4.2 — сам JSX (Statistic, List, разбивка) не меняется, меняется только источник данных.

## 5. Сброс состояния

- [x] 5.1 В обработчике кнопки «Очистить» (и везде, где сейчас сбрасываются `pricingResult`/`pricingError`, например при смене коллекции/типа полотна/реверса) заменить сброс единственной тройки на сброс всех трёх новых троек состояния.

## 6. Проверка

- [x] 6.1 `grep -rn "calculatePrice"` по `frontend/src` — подтвердить отсутствие оставшихся ссылок после удаления (задача 1.5).
- [x] 6.2 `npx tsc -b` и `npm run lint` во `frontend/` — без ошибок.
- [x] 6.3 Вручную через `npm run dev` (с поднятым backend): цена полотна+кромки показывается независимо от готовности короба; цена короба+наличника+добора показывается независимо от готовности полотна; правка фурнитуры не запускает пересчёт полотна/короба (нет лишних сетевых запросов к `/api/leaf-types/*/price` и `/api/frame-types/*/price` в Network-панели браузера при изменении только фурнитуры); итоговая цена и разбивка по компонентам визуально не отличаются от поведения до этого change; кнопка «Очистить» сбрасывает все три результата.
