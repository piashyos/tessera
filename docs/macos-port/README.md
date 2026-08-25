# Tessera for macOS — спецификация порта

Полная техническая спецификация нативного **Swift/SwiftUI** порта Android-приложения Tessera под macOS. Это 1:1 копирование функционала с адаптацией UX под Apple HIG и сохранением общего бэкенда (Firebase/Firestore проект `notes-d3abc`).

## Как читать

Начните с **00-overview.md** (цели, стек, матрица паритета фич), затем по слоям:

| Документ | О чём |
|----------|-------|
| [00-overview.md](00-overview.md) | Цели, scope, технологический стек, полная матрица функционала |
| [01-architecture.md](01-architecture.md) | Слои, структура Swift-проекта, DI, конкурентность, эквиваленты Flow |
| [02-data-model-and-persistence.md](02-data-model-and-persistence.md) | SwiftData-схема (перенос Room v6), сущности, FTS5, миграции, pure-алгоритмы |
| [03-backend-sync-auth-sharing.md](03-backend-sync-auth-sharing.md) | Firestore-контракт, security rules, Google Sign-In, движок синка, шеринг с ролями |
| [04-rich-text-editor.md](04-rich-text-editor.md) | Редактор на NSTextView, Markdown↔AttributedString, форматирование, GFM-таблицы |
| [05-ui-screens-navigation.md](05-ui-screens-navigation.md) | Все экраны, NavigationSplitView, sidebar, список, поиск, настройки |
| [06-design-system.md](06-design-system.md) | Брендовая палитра, типографика (Inter/Instrument Serif), отступы, формы, Lucide→SF Symbols |
| [07-macos-platform.md](07-macos-platform.md) | Меню-бар, горячие клавиши, окна, drag&drop, sandbox, нотаризация |
| [08-implementation-roadmap.md](08-implementation-roadmap.md) | Фазовый план, тестирование, риски, критерии приёмки |

## Ключевые инварианты

1. **Бэкенд не меняется** — Firestore-схема и rules общие с Android; macOS-клиент равноправен.
2. **Markdown — канонический формат** тела заметки; диалект идентичен Android (контракт совместимости).
3. **Чистая бизнес-логика** (синк, merge, роли, нормализация) переносится на Swift 1:1 и покрывается тестами.
4. **UI — идиоматичный для Mac** (sidebar/toolbar/контекстные меню/меню-бар вместо drawer/FAB/bottom sheet/свайпов).

## Источник

Android-приложение `app.tessera` v0.4.1 — Room schema v6, Firebase/Firestore, редактор AztecText, Jetpack Compose + Material 3.
