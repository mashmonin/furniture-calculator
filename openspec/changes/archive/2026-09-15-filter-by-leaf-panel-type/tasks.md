## 1. Псевдо-шаг «Тип полотна»

- [x] 1.1 Добавить `resolvePanelTypeStep(configurations, panelTypeSelection)` в `App.tsx` — по образцу `resolveReverseStep`: скрыт при ≤1 различном `panelType` среди кандидатов, иначе явный выбор с дефолтом «Глухое» (если присутствует, иначе первый встречающийся)
- [x] 1.2 Добавить состояние `panelTypeSelection` (`useState<LeafPanelType | undefined>(undefined)`) и обработчик `handlePanelTypeChange`, сбрасывающий `mirrorFinishTypeId` и весь каскад ниже (по аналогии с `handleCollectionChange`)
- [x] 1.3 Вычислить `panelTypeFilteredConfigurations = reverseFilteredConfigurations.filter(c => c.leaf.panelType === resolvedPanelType)`; `collectionOptions`/`collectionFilteredConfigurations` пересадить на него вместо `mirrorFilteredConfigurations`
- [x] 1.4 Исправить `panelTypeFilteredConfigurations`: при `resolvedPanelType === 'BLIND'` не сужать вовсе (вернуть `reverseFilteredConfigurations` как есть) — по обратной связи («почему глухое только вертикаль и атмосфера») любое полотно исполнимо глухим, независимо от поддержки зеркала/остекления; сужение точным совпадением `panelType` остаётся только для «Зеркальное»/«С остеклением»

## 2. Замена «Нужно зеркало»

- [x] 2.1 Удалить состояние `mirrorFinishEnabled`, константу `MIRROR_FINISH_NEEDED_LABEL`, `Switch` «Нужно зеркало» и его строку в разметке
- [x] 2.2 `resolveMirrorFinishStep`/шаг выбора конкретного исполнения зеркала вызывать только когда `resolvedPanelType === 'MIRRORED'`, от `panelTypeFilteredConfigurations`
- [x] 2.3 Убедиться, что переключение типа полотна на «Глухое»/«С остеклением» после выбора «Зеркальное» сбрасывает `mirrorFinishTypeId` и скрывает шаг исполнения зеркала
- [x] 2.4 Перенести шаг выбора исполнения зеркала в один ряд с каскадным шагом выбора модели полотна («Полотно») — по явному указанию пользователя, соответствует макету Figma (`#13:37`), вместо отдельного блока сразу после «Реверс»

## 3. Подключить существующий переключатель «Тип полотна» к реальному состоянию

- [x] 3.1 Заменить в `OptionGroup` «Тип полотна» (уже отображается рядом с «Коллекция», см. change `show-leaf-panel-type`) источник `options`/`selectedId` с производных от `leafComponent.panelType` на `panelTypeStep.options`/`panelTypeStep.value`, `onChange` — на `handlePanelTypeChange` вместо no-op
- [x] 3.2 Проверить, что переключатель скрывается, когда у согласованных с реверсом конфигураций только один `panelType` (сейчас всегда так, пока не появится 3-е значение, — не должно ломать текущее поведение сильнее, чем предусмотрено design.md) — `panelTypeStep` остаётся `undefined` при ≤1 значении, ряд рендерит только «Коллекцию»

## 4. Проверка

- [x] 4.1 Собрать (`tsc -b --noEmit`) и прогнать `npm run lint` — оба чистые
- [x] 4.2 Проверено пользователем в браузере через `npm run dev` — подтверждено, работает как ожидается
