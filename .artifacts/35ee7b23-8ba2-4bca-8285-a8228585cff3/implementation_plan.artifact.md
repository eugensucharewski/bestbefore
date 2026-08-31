# Улучшение реализации Navigation 3

Этот план направлен на приведение реализации Navigation 3 к современным стандартам: использование DSL для провайдера записей, обеспечение выживания стека при уничтожении процесса и очистка устаревшего кода.

## Proposed Changes

### [Product Components]

#### [MODIFY] [ProductViewModel.kt](file:///C:/Users/sukha/AndroidStudioProjects/BestBefore/app/src/main/java/de/eugens/bestbefore/products/presentation/ProductViewModel.kt)
- Добавление `SavedStateHandle` для сохранения `backStack`.
- Использование сериализации для сохранения списка `UiState`.
- Переименование `cancelScanning` в `popBackStack` для универсальности.

#### [MODIFY] [ProductsScreen.kt](file:///C:/Users/sukha/AndroidStudioProjects/BestBefore/app/src/main/java/de/eugens/bestbefore/products/presentation/ProductsScreen.kt)
- Рефакторинг `NavDisplay` с использованием `entryProvider` DSL.
- Перенос инициализации некоторых ViewModels внутрь соответствующих записей навигации (`NavEntry`) для правильного управления их жизненным циклом.

### [Core]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/sukha/AndroidStudioProjects/BestBefore/app/src/main/java/de/eugens/bestbefore/MainActivity.kt)
- Удаление неиспользуемого и потенциально ошибочного кода `navigationevent`.
- Упрощение структуры `setContent`.

## Verification Plan

### Automated Tests
- Сборка проекта: `gradle build`
- Проверка сериализации `UiState` (визуальный аудит кода).

### Manual Verification
- Проверка корректности переходов между экранами (Main -> Scanning -> Settings).
- Проверка работы кнопки "Назад" (системной и встроенной).
