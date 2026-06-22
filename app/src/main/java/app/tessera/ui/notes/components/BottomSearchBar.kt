package app.tessera.ui.notes.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.components.FloatingCapsule
import app.tessera.core.ui.components.TesseraTextField
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import dev.chrisbanes.haze.HazeState

@Composable
fun BottomSearchBar(
    query: String,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    hazeState: HazeState? = null,
) {
    FloatingCapsule(
        modifier = modifier.heightIn(min = 48.dp),
        blurRadius = 24.dp,
        shadowElevation = 3.dp,
        hazeState = hazeState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        TesseraTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .testTag(UiTestTags.SearchField),
            imeAction = ImeAction.Search,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag(UiTestTags.SearchClear),
                    ) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = stringResource(R.string.search_clear),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}
