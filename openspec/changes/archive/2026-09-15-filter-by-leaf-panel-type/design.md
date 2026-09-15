## Context

См. proposal.md - Why. Текущий механизм в `frontend/src/App.tsx`:
- `resolveReverseStep(configurations, reverseSelection)` — первый шаг потока, boolean, по умолчанию `false`, скрывается если у всех конфигураций одно значение `is_reverse`.
- `resolveMirrorFinishStep(reverseFilteredConfigurations)` + `filterByMirrorFinish(reverseFilteredConfigurations, mirrorFinishEnabled, mirrorFinishTypeId)` — переключатель «Нужно зеркало» (state `mirrorFinishEnabled`, по умолчанию `false`) и последующий выбор конкретного `mirrorFinishTypeId`; выключенное положение НЕ сужает каталог вовсе (в отличие от «Тип полотна», который должен стать настоящей exclusive-партицией).
- `collectionOptions`/`collectionFilteredConfigurations` считаются от `mirrorFilteredConfigurations`.
- `LEAF_PANEL_TYPE_OPTIONS` и `OptionGroup` «Тип полотна» уже существуют (`show-leaf-panel-type`), но `selectedId`/`onChange` — no-op, а сам переключатель завязан на `leafComponent` (уже выбранную модель), а не на `panelType` согласованных конфигураций.

## Goals / Non-Goals

**Goals:**
- Переключатель «Тип полотна» становится настоящим псевдо-шагом каскада (по механике как «Реверс»: скрывается при единственном значении, иначе явный выбор с сохранённым состоянием), заменяя «Нужно зеркало».
- Выбор «Зеркальное» воспроизводит точное прежнее поведение «Нужно зеркало» = включено (сужение + раскрытие шага исполнения зеркала), не меняя сам механизм выбора исполнения и расчёт стоимости.
- «Глухое» — выбор по умолчанию, когда доступен (по макету Figma).

**Non-Goals:**
- Справочник видов остекления, наценка за остекление, появление реальных leaf_type с типом «остеклённое» — отдельный будущий change (см. proposal.md, Impact).
- Любые изменения backend, расчёта стоимости, `ComponentSelectionDto`/`PricingRequestDto`.
- Изменение самого шага выбора конкретного исполнения зеркала (mirror_finish_type) — только условие его раскрытия.

## Decisions

- **Замена `mirrorFinishEnabled` на выбранный `panelType`**: новая функция `resolvePanelTypeStep(reverseFilteredConfigurations, panelTypeSelection)`, зеркалящая `resolveReverseStep` по форме:
  ```
  function resolvePanelTypeStep(configurations, panelTypeSelection) {
    const availableTypes = уникальные configuration.leaf.panelType среди configurations (без null)
    if (availableTypes.size <= 1) {
      return { resolvedPanelType: availableTypes[0] ?? undefined } // шаг скрыт
    }
    const resolved = panelTypeSelection ?? (availableTypes.has('BLIND') ? 'BLIND' : [...availableTypes][0])
    return { panelTypeStep: { visible: true, options: availableTypes, value: resolved }, resolvedPanelType: resolved }
  }
  ```
  Состояние — `const [panelTypeSelection, setPanelTypeSelection] = useState<LeafPanelType | undefined>(undefined)`, сбрасывается (`setPanelTypeSelection(undefined)`) там же, где сейчас сбрасывается `reverseSelection`/`mirrorFinishEnabled` (при смене реверса — только пересчёт, не сброс выбора клиента, см. spec; при смене более ранних шагов, которых перед ним нет, сброса не требуется).
- **`panelTypeFilteredConfigurations`** заменяет `mirrorFilteredConfigurations`, с поправкой на несимметричность вариантов (по обратной связи после первой реализации — «Глухое» не отдельная непересекающаяся категория, а базовое исполнение, доступное любому полотну, в т.ч. зеркальным моделям): `resolvedPanelType === 'BLIND' ? reverseFilteredConfigurations : reverseFilteredConfigurations.filter(c => c.leaf.panelType === resolvedPanelType)`. Иными словами, «Глухое» не сужает вовсе (та же семантика, что была у выключенного «Нужно зеркало»), а «Зеркальное»/«С остеклением» сужают точным совпадением panelType, как и раньше. `collectionOptions` считаются от результата.
- **Шаг исполнения зеркала**: `resolveMirrorFinishStep` остаётся как есть (сигнатура/логика не меняется), но вызывается только когда `resolvedPanelType === 'MIRRORED'`, от `panelTypeFilteredConfigurations` вместо `reverseFilteredConfigurations`; видимость дополнительно гарантируется существующей проверкой `options.length > 0` внутри неё.
- **`OptionGroup` «Тип полотна»** (уже существует в разметке рядом с «Коллекция», см. `show-leaf-panel-type`) — заменить источник `selectedId`/`options` с производных от `leafComponent.panelType` на `panelTypeStep.value`/`panelTypeStep.options`, `onChange` — на реальный `handlePanelTypeChange`, сбрасывающий `mirrorFinishTypeId` и все шаги каскада ниже (по аналогии с `handleCollectionChange`/`handleReverseChange`).
- **Удаляется**: `mirrorFinishEnabled` state, `MIRROR_FINISH_NEEDED_LABEL`, `Switch` «Нужно зеркало» и его строка в разметке (реверс остаётся один в своём ряду — по макету).
- **Место шага исполнения зеркала (по явному указанию пользователя, макет Figma `#13:37`)**: не отдельный блок сразу после «Реверс» (как было у «Нужно зеркало»), а один ряд с каскадным шагом выбора модели полотна («Полотно») — `renderCascadeStep`, ветка `step.key === 'leaf'`, тот же `flex`-ряд паттерн, что уже используется для «Коллекция»+«Тип полотна». Рендерится только когда `mirrorFinishStep.visible` (т.е. `resolvedPanelType === 'MIRRORED'`).
- **Порядок сужения** не меняется относительно текущего (Реверс → [то, что раньше было «Нужно зеркало»] → Коллекция → …), меняется только МЕХАНИЗМ этого второго шага.

## Risks / Trade-offs

- [Изначально предполагалось, что «Глухое» по умолчанию точно так же сужает каталог, как «Зеркальное»/«С остеклением» — до leaf_type с panelType «Глухое». Это оказалось неверно: пользователи ожидают, что любое полотно можно заказать глухим, включая модели, которые дополнительно поддерживают зеркало (см. обратную связь — «почему глухое только вертикаль и атмосфера»)] → исправлено: «Глухое» не сужает каталог вовсе (полное совпадение с прежним поведением выключенного «Нужно зеркало»), сужают только «Зеркальное»/«С остеклением». Формально это по-прежнему **BREAKING** относительно поведения до этого change (переключатель «Нужно зеркало» пропадает целиком), но НЕ ограничивает набор коллекций/моделей сильнее, чем было — наоборот, «Глухое» теперь показывает строго тот же набор, что показывался при выключенном «Нужно зеркало».
- [Вариант «С остеклением» в переключателе сейчас никогда не появится — 0 leaf_type такого типа] → ожидаемо и безопасно: переключатель просто не покажет этот сегмент, пока не появятся такие leaf_type (см. Non-Goals); код не завязан на фиксированный список из 3 вариантов, а вычисляет их из фактических данных, так что появление остеклённых моделей позже не потребует правок этого механизма.
