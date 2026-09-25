## Context

См. proposal.md — Why. `buildCartItemContent()` (`frontend/src/ConfiguratorScreen.tsx`) уже строит `CartItemContent` (`displayName`, `dimensionsLabel`, `exportRequest`, `pricingSnapshot`, `detailRows`) из текущего состояния конфигуратора в момент добавления в корзину/живой синхронизации — все данные, нужные для новых тегов, уже читаются в этой функции или доступны в замыкании компонента: `resolvedReverse`, `resolvedHasQuarter`, `resolvedPanelType` (`'BLIND' | 'GLAZED' | 'MIRRORED'`), `leafThicknessValue` (уже вычисляется в этой же функции для `dimensionsLabel`), `selection.leaf.doubleSidedPainting`.

## Goals / Non-Goals

**Goals:**
- Список применимых тегов конфигурации, вычисленный на фронтенде без нового обращения к backend.

**Non-Goals:**
- Не вводится настраиваемый/расширяемый реестр тегов — список из шести конкретных атрибутов, зашитый в код (как и уже существующие `SERVICE_TAG_LABEL`/`ITEM_STATUS_LABEL`).
- Не меняется backend/API, не меняется формат выгрузки в Excel — теги только для отображения в столбце «Конфигурация» на экране корзины.

## Decisions

- **Новое поле `attributeTags: string[]`** в `CartItemContent` (`frontend/src/cart.ts`) — плоский список уже отфильтрованных, готовых к отображению строк-тегов (а не шесть отдельных булевых полей) — проще сериализовать в `localStorage` и рендерить в `CartScreen.tsx` без повторной логики выбора применимых тегов на экране корзины.
- **Точки определения**:
  - «РЕВЕРС» — `resolvedReverse === true`.
  - «ЧЕТВЕРТЬ» — `resolvedHasQuarter === true`.
  - «ОСТЕКЛЕНИЕ» / «ЗЕРКАЛО» — `resolvedPanelType === 'GLAZED'` / `'MIRRORED'` соответственно (третье значение, `'BLIND'`, тега не даёт); взаимоисключающие по построению, так как `resolvedPanelType` — одно значение.
  - «ТОЛЩИНА 59» — `leafThicknessValue === THICKNESS_REQUIRING_QUARTER_MM` (переиспользуется существующая константа `= 59`, а не новое магическое число).
  - «ДВУСТОРОННЯЯ» — `selection.leaf.doubleSidedPainting === true`.
- Порядок тегов в массиве — фиксированный (РЕВЕРС, ОСТЕКЛЕНИЕ/ЗЕРКАЛО, ЧЕТВЕРТЬ, ТОЛЩИНА 59, ДВУСТОРОННЯЯ), неприменимые пропускаются, а не заменяются пустой строкой.
- **Место в UI**: `CartScreen.tsx` рендерит `attributeTags` в том же `<Space>` с тегами, где уже есть `SERVICE_TAG_LABEL`/`ITEM_STATUS_LABEL`, после них — без нового ряда/обёртки.

## Risks / Trade-offs

- [Позиции, добавленные в корзину до этого изменения, не имеют `attributeTags` в `localStorage`] → Как и с `detailRows` ранее (см. change add-order-cart-screen, группа 11) — отсутствующее поле трактуется как пустой список (нет дополнительных тегов), без ошибки; данные становятся точными только для новых/пересинхронизированных позиций.
