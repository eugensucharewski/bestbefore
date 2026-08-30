# Восстановление предсказательной навигации (Predictive Back)

Этот план исправляет работу жеста «Назад», добавляя необходимые флаги в Manifest и корректируя логику обработки переходов в Compose для поддержки системных анимаций Android 13+.

## Proposed Changes

### [Core]

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/sukha/AndroidStudioProjects/BestBefore/app/src/main/AndroidManifest.xml)
- Добавление `android:enableOnBackInvokedCallback="true"` в секцию `<application>`.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/sukha/AndroidStudioProjects/BestBefore/app/src/main/java/de/eugens/bestbefore/MainActivity.kt)
- Добавление вызова `enableEdgeToEdge()` для корректной работы системных баров и жестов.

### [Navigation]

#### [MODIFY] [ProductsScreen.kt](file:///C:/Users/sukha/AndroidStudioProjects/BestBefore/app/src/main/java/de/eugens/bestbefore/products/presentation/ProductsScreen.kt)
- Изменение логики `onBack` в `NavDisplay`: обработчик будет передаваться только в том случае, если в стеке больше одного экрана или есть выделенные элементы. Это позволит системному жесту «Назад» работать корректно на главном экране (для выхода из приложения с анимацией).

## Verification Plan

### Manual Verification
- **Жест «Назад» на внутренних экранах:** Проверить, что при начале свайпа от края экрана появляется предпросмотр предыдущего экрана (если поддерживается OS).
- **Жест «Назад» на главном экране:** Проверить, что при свайпе появляется анимация сворачивания приложения в иконку (Predictive Back Exit).
- **Edge-to-Edge:** Убедиться, что контент заходит под системные бары (статус-бар и навигационная панель) и они стали прозрачными.
