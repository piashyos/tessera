package app.tessera.ui.editor.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Baselines of the native editor body. `mockup` is the content of the frames `editor-content-{light,dark}` (the same words), `all`
 * has every paragraph kind, every mark, nesting, an object of each sort and both check states.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class NoteBodyScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val mockup = """
        # Q3 roadmap — приоритеты
        Список приоритетов для третьего квартала. Обсудили на ретро 11 июня, договорились вернуться через две недели.
        ## Что точно делаем
        - [x] Закрыть онбординг для новых пользователей
        - [ ] Доделать invisible sync (текущая ветка)
        - [ ] Разобраться с CRDT для офлайн-конфликтов
        - [ ] Material You редизайн
        ## Под вопросом
        Голосовой ввод и шифрование заметок — отдельным треком, не блокируем релиз.
        > «Сначала закрываем долги по UX, потом расширяемся.»
        ## Связанное
        ```
        git log --oneline feature/sync
        baae2d4 fix(prefs): avoid loop
        10cf6f3 feat(settings): account block
        ```
    """.trimIndent()

    private val all = """
        # Название заметки
        Основной текст: **жирный**, *курсив*, <u>подчёркнутый</u>, ~~зачёркнутый~~, `код`, **~~жирный зачёркнутый~~** и <u>*курсив с подчёркиванием*</u> в одной строке, которая переносится на следующую.
        ## Заголовок
        ### Подзаголовок
        - Точка
            - Точка, уровень 1
                - Точка, уровень 2
        + Тире
            + Тире, уровень 1
        1. Первый
        2. Второй
            1. Вложенный
        - [ ] Не отмечено
        - [x] Отмечено
            - [ ] Вложенный чек-лист
        > Цитата в одну строку
        > и вторая строка цитаты
        ```
        mono line one
        mono line two
        ```
        ---
        ![](tessera-img://abc123)
        <!-- tessera:cols=2;rows=1 -->
        | a | b |
        | --- | --- |
        | 1 | 2 |
        Последний абзац
    """.trimIndent()

    private fun capture(name: String, md: String, dark: Boolean, editable: Boolean = true) {
        rule.setContent {
            TesseraTheme(darkTheme = dark, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoteBody(
                        markdown = md,
                        onMarkdownChange = {},
                        onActionsReady = {},
                        onFormatStateChanged = {},
                        editable = editable,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("ui/editor/note_body_$name.png")
    }

    @Test fun mockupContentLight() = capture("mockup_light", mockup, dark = false)
    @Test fun mockupContentDark() = capture("mockup_dark", mockup, dark = true)
    @Test fun allKindsAndMarksLight() = capture("all_light", all, dark = false)
    @Test fun allKindsAndMarksDark() = capture("all_dark", all, dark = true)
    @Test fun readOnlyLight() = capture("readonly_light", mockup, dark = false, editable = false)
}
