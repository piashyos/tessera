package app.tessera.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraShape

/**
 * The app's single, unified text input: a soft filled box (no hard border) on the
 * `surfaceContainerHigh` tier with the shared [TesseraShape.input] shape. Wraps [TesseraTextField]
 * so every field across the app reads the same — use this instead of raw M3 `TextField` /
 * `OutlinedTextField` or one-off `Surface`-wrapped fields.
 *
 * Pass [shape] = `TesseraShape.pill` for a single-line pill/search input; the default is the
 * 16dp rounded box used for everything else.
 */
@Composable
fun TesseraInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    shape: Shape = TesseraShape.input,
    imeAction: ImeAction = ImeAction.Default,
    keyboardType: KeyboardType = KeyboardType.Text,
    onImeAction: () -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    minHeight: Dp = 52.dp,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = modifier,
    ) {
        TesseraTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = singleLine,
            imeAction = imeAction,
            keyboardType = keyboardType,
            onImeAction = onImeAction,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
