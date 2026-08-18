# Furniture Calculator

Приложение для расчёта параметров и стоимости мебели. Монорепозиторий: `backend/` (Spring Boot API) + `frontend/` (React SPA).

## Язык

Все общение с пользователем и вся документация в рамках этого проекта — только на русском языке. Это касается ответов в чате, комментариев в коде (если они добавляются), commit-сообщений, содержимого `openspec/` (proposals, design, specs, tasks) и любых markdown-файлов. Идентификаторы в коде (имена классов, переменных, методов), названия пакетов и технические термины без устоявшегося русского аналога остаются на английском.

## Стек

- **Backend**: Java 21, Spring Boot 4.1.x, Gradle (Groovy DSL). Каталог `backend/`.
- **Frontend**: React + TypeScript, Vite. UI — только компоненты Ant Design (`antd`), других UI-библиотек не добавлять. Каталог `frontend/`.
- **База данных**: PostgreSQL. Схема управляется исключительно через Liquibase — миграции в `backend/src/main/resources/db/changelog/`, master-файл `db.changelog-master.yaml`. Ручных правок схемы (DDL мимо Liquibase) быть не должно.
- **Локальная разработка**: `docker-compose.yml` в корне поднимает PostgreSQL (`localhost:5434`, база/юзер/пароль — `furniture_calculator`).

## Структура репозитория

```
backend/    Spring Boot приложение (Gradle wrapper внутри: ./backend/gradlew)
frontend/   Vite + React + TS приложение (npm внутри: frontend/package.json)
openspec/   Спецификации и change-proposals (spec-driven workflow)
```

## Команды

Backend (из `backend/`):
- `./gradlew bootRun` — запустить API
- `./gradlew test` — тесты
- `./gradlew build` — сборка

Frontend (из `frontend/`):
- `npm run dev` — dev-сервер
- `npm run build` — прод-сборка (`tsc -b && vite build`)
- `npm run lint` — линт

Инфраструктура (из корня):
- `docker compose up -d` — поднять PostgreSQL для локальной разработки

## Конвенции

- Backend: пакет `com.example.furniturecalculator`, Lombok разрешён, слоистая архитектура (controller/service/repository).
- Любое изменение схемы БД — новый Liquibase changeset, подключённый в master changelog. Не редактировать уже применённые (заархивированные через `openspec archive` или уже выпущенные) changeset'ы задним числом — только новые.
- Frontend: функциональные компоненты, строго TypeScript, компоненты интерфейса — из `antd` (не писать кастомные аналоги существующих antd-компонентов).
- REST API без серверного рендеринга — только JSON.

## Рабочий процесс (spec-driven, OpenSpec)

Перед реализацией нетривиальной фичи или изменения схемы — сначала proposal в `openspec/`, потом код:

1. `/opsx:propose "..."` — предложить изменение (описание, зачем, что меняется).
2. Ревью и правки предложения по необходимости (`/opsx:update`).
3. `/opsx:apply` — реализовать одобренное изменение.
4. `/opsx:archive` — заархивировать завершённое изменение и обновить основные specs.

Мелкие правки (опечатки, форматирование, локальный рефактор без изменения поведения/схемы) можно делать без прохождения через openspec.

## Git

- Отдельный git-репозиторий (не часть монорепо DemoProject). Ветка по умолчанию — `main`.
- Разработка — через feature-ветки, коммиты — осмысленные и атомарные.
