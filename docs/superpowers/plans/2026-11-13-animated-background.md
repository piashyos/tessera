# План: живой фон (градиент от касаний) и размер зерна

Ветка `feature/animated-background`, worktree `~/.config/superpowers/worktrees/notes/animated-background`, база `7ec94b4`.
Ведёт оркестратор (feature-team). Пользователь: «ок делай все дальше без меня» (2026-11-13).

## 1. Источники

- Спека: `docs/superpowers/specs/2026-11-13-animated-background-design.md` (3 круга ревью).
- Макет: кадров под фичу нет. Фон в `design/mockups` не нарисован, в `settings-light.html` / `settings-dark.html` нет
  даже строк зерна. Все состояния — «без макета», по спеке; скрины — пользователю в финальном отчёте.

## 2. Спека против макета, как делаем

Расхождений нет — макета нет. Решения пользователя (2026-11-13, дословно — выбор в вопросах):
- цвета — «Из палитры темы (Рекомендую)»;
- движение — «Волна по жесту (Рекомендую)»;
- по умолчанию — «Включён (Рекомендую)»;
- дизайн целиком (вкл. «вдох» на тап, AMOLED без градиента, ×1–×4 зерна) — «ок делай все дальше без мееня».

## 3. Решения

- D1. Room и Firestore не меняются. Две новые настройки — DataStore в `UserPreferencesRepository` (ключи
  `background_gradient_enabled` = true, `grain_size` = 1, чтение/запись ограничены 1..4).
- D2. `GrainOverlay` (общий, `core/ui/theme`) расширяется только необязательными параметрами с прежним поведением
  по умолчанию: `grainSize = 1`, `gradientEnabled = false`, `motion = rememberBackdropMotionState()`.
- D3. Модель движения — чистые функции и `BackdropMotionState` в `core/ui/theme/BackdropMotion.kt` (спека §4.2–§4.3).
- D4. Плитка зерна — чистая `grainTile(grainSize)` в `core/ui/theme/GrainTile.kt`; при 1 — побитно как сейчас.
- D5. AMOLED + тёмная тема → градиент не рисуется (решает `MainActivity`).
- D6. Три fake `UserPreferences` правит роль `data`, включая приватные в `ui/settings/SettingsViewModelTest.kt` и
  `ui/notes/NotesListViewModelTest.kt` (вне её обычной зоны — назначено здесь, иначе unit-тесты не скомпилируются).
- D7. Устройство команды — `emulator-5556` (AVD `Small_Phone`). `emulator-5554` — пользовательский, не трогать.
- D8. Связь с ролями — только файлами `.claude/local/msg-to-orchestrator-<роль>-<N>.md` + `--resume` (memory
  `feature-team-cross-mode-messaging`).

## 4. Реестр компонентов

| Элемент | Ресёрч (codebase-memory) | Решение | Роль |
| --- | --- | --- | --- |
| Фон приложения (цвет + зерно) | `GrainOverlay` в `core/ui/theme`, вызывается только из `MainActivity` | расширяем `GrainOverlay` необязательными параметрами; слои по спеке §3 | ui |
| Пятна градиента + движение | аналогов нет (`search_code` «hazeEffect», «Animatable<Offset>»: только попап и капсула) | новый `BackdropMotion.kt` в `core/ui/theme` | ui |
| Плитка зерна | генерация внутри `GrainOverlay` | выносим в `GrainTile.kt` (та же логика + размер) | ui |
| Строка «Градиент фона» | `SettingsRow` + `Switch` (как «Зернистость») в `SettingsScreen.AppearanceSection` | берём `SettingsRow` | feature |
| Ползунок «Размер зерна» | приватная `GrainIntensityRow` | расширяем её параметрами (подпись, `valueRange`, `steps`), не копируем | feature |

## 5. Срезы

### Chunk 1 — `data`: настройки

- [x] `UserPreferences`: `gradientEnabled`, `cachedGradientEnabled`, `setGradientEnabled`; `grainSize`, `cachedGrainSize`,
  `setGrainSize`. `UserPreferencesRepository` — ключи, кэш, ограничение 1..4. Три fake (D6).
- [x] `UserPreferencesRepositoryTest`: умолчания, запись/чтение, ограничение `grain_size` (0 → 1, 9 → 4).
  Принято 2026-11-13: `core.preferences` + `ui.settings` + `ui.notes` — 113/0 (14 классов, `--rerun`, сам).
  Оркестратор поправил пробел в `KEY_LANG =`.

Готово, когда: модуль компилируется; `core.preferences.*`, `ui.settings.*`, `ui.notes.*` unit — зелёные (числа `>> gw`).

### Chunk 2 — `ui`: фон, движение, плитка (после приёмки Chunk 1: пока три fake не обновлены, unit-исходники не компилируются)

- [x] `GrainTile.kt` + `GrainTileTest` (спека §5, §8). Chunk 2.1: сетка всегда 160 × 160 (при ×4 старая 40 × 40
  давала узор «обоев» — поймано оркестратором на скриншоте); мутация старой реализации роняет 2 из 4.
- [x] `BackdropMotion.kt`: `nudge`, константы, `BackdropMotionState`, `rememberBackdropMotionState` (спека §4.2–§4.3)
  + `BackdropMotionTest`.
- [x] `GrainOverlay`: слои (§3), слушатель (§4.1), защита от «Удалить анимации» (§4.2), KDoc. Принятые отклонения:
  пятно — `drawCircle` в `translate`/`scale` (без шва у края); на отпускании `onRelease()` + `onTap()` без сдвига;
  `grainTile` зажимает размер 1..4.
- [x] `GrainOverlayGestureTest` (6), `GrainOverlayReducedMotionTest` (1); мутации защиты и отмены роняют тесты.
- [x] Roborazzi `GrainOverlayScreenshotTest` (4): golden записал оркестратор после просмотра, verify 4/0.
  Принято 2026-11-13: `core.ui.*` 109/0 (28 классов), `core.ui.theme` verify 48 (4 новых без golden до записи).

Готово, когда: модуль компилируется; `core.ui.*` unit — зелёные; новые скриншот-тесты перечислены без golden.

### Chunk 3 — `feature`: настройки на экране и связка (после приёмки 1 и 2)

- [x] `SettingsState` (+2 поля), `SettingsViewModel` (`ThemePrefs` + сеттеры), `SettingsScreen` / `AppearanceSection`
  (строка градиента, ползунок размера через расширенную `GrainIntensityRow`), `NotesNavGraph` (связка), строки en/ru.
- [x] `MainActivity`: `produceState` для двух настроек; `GrainOverlay(…, grainSize, gradientEnabled && !(amoled && resolvedDark))`.
- [x] `SettingsViewModelTest`: новые поля и сеттеры (+2). `SettingsScreenshotTest` — 6 golden перезаписал оркестратор
  после просмотра (`ok_light/dark`, `syncing`, `offline`, `error`, `signed_out`; отличия только в «Внешний вид»), verify 8/0.
  Принято 2026-11-13: `core.*` + `ui.settings.*` + `ui.notes.*` — 406/0 (52 класса). Первый такой прогон дал 80
  падений и на повторе 406/0 — нестабильность Robolectric NATIVE (memory `robolectric_native_graphics_flake`).
  Robolectric-теста интеракции экрана настроек нет (как и раньше) — переключение и ползунок проверит qa на устройстве.

Готово, когда: модуль компилируется; `ui.settings.*`, `ui.notes.*`, `core.ui.*` unit — зелёные; verify
`SettingsScreenshotTest` — список отличий.

### Chunk 4 — `qa` (emulator-5556; можно начинать после приёмки 1–2, финальные прогоны — после 3)

- [ ] Спека §8 on-device: жесты экранов не сломаны (`ui.notes`, `ui.components`, `ui.editor.screen`), перетаскивание
  меняет пиксели пустой области и они возвращаются, печать не меняет, `animator_duration_scale` ≠ 0.
- [ ] Плавность: на emulator-5556 «janky %» ~99 % и без градиента (замер 2026-11-13), поэтому критерий — 50-й и 90-й
  перцентиль времени кадра из `gfxinfo` при одинаковом скролле списка, градиент вкл против выкл; рост p50/p90 больше
  чем на 20 % — замечание. Итог на эмуляторе — ориентир, решает настоящий телефон (в «не проверено»).
- [ ] Базовая линия падений (красные и без фичи, не наши): `NativeTableEditTest.withSeveralTablesEachOverlayStaysOnItsTableWhileTheNoteScrolls`,
  3 × `NotesListScreenTest` (`openingSecondCardCollapsesFirst`, `fabTapInvokesCreate`, `scrollingCollapsesOpenCard`); нестабильные:
  `NoteBodyTableGateTest.gate`, `NativeTableEditTest.everyButtonOfTheTableBarIsOneUndoStep`. Любое другое падение — сначала
  прогон с выключенным градиентом, потом выводы. Отдельно смотреть `NativeEditorRacesTest` (guard по часам < 300 мс).
- [ ] `AppearanceAppTest.theme()` выключает и градиент (по образцу зерна).
- [ ] Скрины: light / dark × 3 палитры, AMOLED (градиента нет), градиент выкл, зерно ×1 и ×4, настройки (ru и en).
- [ ] `docs/superpowers/qa/animated-background-gestures.md` (числа), `…-design-diff.md` («без макета», скрины).

### Ревью

`review` (только чтение) — после приёмки 1+2 и после 3. Финал — новой сессией по всему диффу.

- [x] Ревью Chunk 1–3 (2026-11-13, `msg-to-orchestrator-review-1.md`): блокеров 0, заметных 2, мелочей 2. Всё
  поправил оркестратор (по 2–5 строк, код из ревью):
  - тест «перетаскивание — не тап» (масштаб 1 всё время после `up`) в `GrainOverlayGestureTest`;
  - тест умолчаний репозитория — кэш свежего экземпляра (иначе читал то, что записал `setUp`); тест ограничения
    переименован в `grain size is clamped to 1 to 4`;
  - `finally { if (active) motion.onCancel() }` — снятие слушателя посреди жеста работает как отмена;
  - en-строка «colours» → «colors» (и в спеке/плане).
  Итог: 3 класса 15/0. (Мутация «перетаскивание тоже вдыхает» снята — «вдох» на тап заменён импульсом, Chunk 2.2.)

### Chunk 2.2 — `ui`: импульс на любое нажатие (пользователь 2026-11-13)

«но почему при кликах на заметки фон никак не реагирует, он должен реагировать на все».
- [x] `onPress(position)` на первом `changedToDown`: `pulse` 0→1→0 (~1.5 с), пятна отталкиваются от точки нажатия
  (`pulsePush`, амплитуды 0.6 / 0.45 / 0.3 × limit), масштаб ×1.15; `onTap`/touchSlop убраны. Спека §4.1–§4.3, §8.
  Принято 2026-11-13: `core.ui.*` 112/0 (роль), `core.ui.theme.*` 51/0 (сам), verify `core.ui.theme` 51/0 — скриншоты
  в покое не изменились. Мутации (сам, qa простаивала): импульс к пальцу — падают 3; импульс только на отпускании —
  падает 1.

### Chunk 2.3 — оркестратор: подбор силы движения на устройстве (2026-11-13)

QA (`qa/animated-background-design-diff.md`): градиент в покое — как задумано, а движение ниже порога восприятия
(2–4 уровня из 255; пользователь: «фон никак не реагирует»).
- [x] Вариант qa A: `BackdropLimitFraction` 0.09 → 0.2, `PulseAmplitude` 1.0 / 0.8 / 0.6, `PulseScale` 1.3 —
  замер 4–5 уровней (тинт в покое отличается от фона всего на 10–12 уровней, сдвиг не может дать больше).
- [x] Импульс «светит»: кисти с альфой `blobAlpha × PulseGlow` (2), рисуются с `pulseGlowAlpha(pulse)` — в покое прежний
  тинт (golden в покое не изменились, verify 4/0), на пике вдвое насыщеннее. Спад импульса — `spring(0.8, 20,
  threshold 0.02)`: почти без перелёта, ~1.4 с (с 0.6 / 20–30 светящийся хвост не успевал за 2.5 с).
- [x] Замер (`BackgroundGradientTest`, emulator-5556): тап — 9 уровней на 100 % полосы через 400 мс, через 2.5 с покой
  (0 px); перетаскивание — 4–5 уровней на 95 % полосы + импульс в начале жеста; печать — 0; выкл / AMOLED /
  «Удалить анимации» — 0. Итог 9/0. Unit `core.ui.theme.*` 52/0 (+1 тест `pulseGlowAlpha`).

### Финальное ревью (новая сессия, 2026-11-13) — блокеров 0, заметных 3 (все про тесты), поправил оркестратор

- [x] `NativeAppTest`: классы редактора всегда идут с градиентом как у пользователя (вкл.), `-e gradient off` выключает —
  раньше наследовали значение, оставленное предыдущим классом.
- [x] Тест `switchingTheGradientOffMidDragReturnsTheBlobsToRest`. Мутации: без `finally` тест зелёный — при снятии узла
  Compose ui 1.11.3 сам присылает отмену (её ловит ветка отмены); без обоих путей падает (и `cancelAfterDrag…`). `finally`
  оставлен как запасной путь на случай другого поведения Compose.
- [x] `BackdropMotionState.blobShift(i, center, limitPx)` — тот же сдвиг, что рисует слой пятен; тест направления импульса
  читает его. Мутация «рисуемый импульс к пальцу» роняет `pressPushes…`.
- Итог: `core.ui.theme.*` 53/0, androidTest компилируется.

## 6. Строки

| Ключ | en | ru |
| --- | --- | --- |
| `settings_gradient_title` | Background gradient | Градиент фона |
| `settings_gradient_subtitle` | Soft colors that move with your touch | Мягкие цвета, которые двигаются от касаний |
| `settings_grain_size` | Grain size | Размер зерна |

## 7. Финал

Шаг 8 скилла: полный `testDebugUnitTest --rerun`, полный `verifyRoborazziDebug`, сам на устройстве главный сценарий,
новая сессия `review`, advisor, отчёт.

## 8. Открытые вопросы и кандидаты на уборку

- Альфы пятен и жёсткости пружин — константы, подбираются на устройстве (спека §3, §4.2); итог — сюда.
