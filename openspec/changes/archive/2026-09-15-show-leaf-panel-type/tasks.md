## 1. Типы и данные

- [x] 1.1 Добавить поле `panelType: 'BLIND' | 'GLAZED' | 'MIRRORED' | null` в интерфейс `ComponentCatalogDto` (`frontend/src/api/types.ts`)

## 2. Отображение

- [x] 2.1 Добавить словарь `LEAF_PANEL_TYPE_LABELS` (коды → «Глухое»/«Остеклённое»/«С зеркалом») в `frontend/src/App.tsx`
- [x] 2.2 В карточке «Параметры выбранного полотна» (`renderCascadeStep`, ветка leaf) отобразить подпись типа полотна для `component.panelType`, если компонент выбран; если не выбран — подпись не рендерится
