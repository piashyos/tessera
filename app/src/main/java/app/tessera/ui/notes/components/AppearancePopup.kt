package app.tessera.ui.notes.components

import android.icu.text.BreakIterator
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.components.FloatingCapsule
import app.tessera.core.ui.components.TesseraPopup
import app.tessera.core.ui.components.TesseraTextField
import app.tessera.core.ui.tessera.EntityIcon
import app.tessera.core.ui.tessera.EntityIconView
import app.tessera.core.ui.tessera.LocalTagAppearances
import app.tessera.core.ui.tessera.LucideIconSet
import app.tessera.core.ui.tessera.TagChipColors
import app.tessera.core.ui.tessera.chipColors
import app.tessera.core.ui.tessera.isAcceptableEmoji
import app.tessera.core.ui.tessera.parseEntityIcon
import app.tessera.core.ui.tessera.rememberTagChipColors
import app.tessera.core.ui.tessera.toRaw
import app.tessera.core.ui.theme.AccentColor
import app.tessera.core.ui.theme.TesseraSpacing
import dev.chrisbanes.haze.HazeState

/**
 * Glass popup that sets the colour and icon of a tag or a folder (and the name of a folder).
 * Creating a folder, editing one and styling a tag are the same popup: [name] `null` hides the
 * name field (a tag), [showReset] adds "Reset" (a tag and an existing folder).
 *
 * The popup only reports what was chosen — [onSave] gets `(name, colorKey, iconRaw)`; whether the
 * save worked is the caller's business ([nameTaken] shows the error under the name field, and
 * [onNameEdited] tells the caller to hide it again). Swipe, scrim and back close without saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearancePopup(
    visible: Boolean,
    title: String,
    name: String?,
    initialColor: String?,
    initialIcon: String?,
    onSave: (name: String, color: String?, icon: String?) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    showReset: Boolean = false,
    nameTaken: Boolean = false,
    onNameEdited: () -> Unit = {},
) {
    var nameText by remember(visible, name) { mutableStateOf(name.orEmpty()) }
    var color by remember(visible, initialColor) { mutableStateOf(AccentColor.fromKey(initialColor)) }
    var icon by remember(visible, initialIcon) { mutableStateOf(parseEntityIcon(initialIcon)) }
    var emojiText by remember(visible, initialIcon) {
        mutableStateOf((parseEntityIcon(initialIcon) as? EntityIcon.Emoji)?.text.orEmpty())
    }
    // A key this version does not know (written by a newer one) reads as "none" here; saving without
    // touching the field must hand it back unchanged, not erase it.
    var colorTouched by remember(visible, initialColor) { mutableStateOf(false) }
    var iconTouched by remember(visible, initialIcon) { mutableStateOf(false) }
    val canSave = name == null || nameText.trim().isNotBlank()
    val submit: () -> Unit = {
        if (canSave) {
            onSave(
                nameText.trim(),
                if (colorTouched) color?.key else initialColor,
                if (iconTouched) icon?.toRaw() else initialIcon,
            )
        }
    }

    TesseraPopup(
        visible = visible,
        onDismiss = onDismiss,
        hazeState = hazeState,
        modifier = modifier,
        footer = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.folders_cancel))
            }
            Button(
                onClick = submit,
                enabled = canSave,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.folders_save))
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showReset) {
                TextButton(
                    onClick = {
                        color = null
                        icon = null
                        emojiText = ""
                        colorTouched = true
                        iconTouched = true
                    },
                ) {
                    Text(stringResource(R.string.appearance_reset))
                }
            }
        }
        if (name != null) {
            Column(verticalArrangement = Arrangement.spacedBy(TesseraSpacing.xs)) {
                FloatingCapsule(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    TesseraTextField(
                        value = nameText,
                        onValueChange = {
                            nameText = it
                            onNameEdited()
                        },
                        placeholder = stringResource(R.string.folders_new_hint),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 24.dp),
                        imeAction = ImeAction.Done,
                        onImeAction = submit,
                    )
                }
                if (nameTaken) {
                    Text(
                        text = stringResource(R.string.folders_name_taken),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = TesseraSpacing.lg),
                    )
                }
            }
        }

        SectionLabel(R.string.appearance_color)
        // 40 dp slots around 32 dp dots: the 8 dp visual step (TesseraSpacing.sm) with a touch target that is not tiny.
        // Opens scrolled so that the saved colour is in view (the 11 slots are wider than the card).
        val slotPx = with(LocalDensity.current) { 40.dp.toPx() }.toInt()
        val colorScroll = rememberScrollState(initial = slotPx * ((AccentColor.fromKey(initialColor)?.ordinal ?: -1) + 1))
        Row(modifier = Modifier.horizontalScroll(colorScroll)) {
            NoColorDot(
                selected = color == null,
                label = stringResource(if (name == null) R.string.appearance_color_auto else R.string.appearance_color_none),
                onClick = {
                    color = null
                    colorTouched = true
                },
            )
            AccentColor.entries.forEach { accent ->
                ColorDot(
                    accent = accent,
                    selected = color == accent,
                    onClick = {
                        color = accent
                        colorTouched = true
                    },
                )
            }
        }

        SectionLabel(R.string.appearance_icon)
        val iconTint = color?.chipColors()?.content ?: MaterialTheme.colorScheme.onSurfaceVariant
        // A FlowRow, not a lazy grid: a lazy grid inside the popup's scrolling column would scroll against the swipe-to-close.
        // As many 44 dp cells per row as fit (7 on a wide card, 6 at 360 dp), sharing the width evenly (no dead strip
        // on the right); the last row is topped up with empty cells so its items stand in the columns above.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = maxOf(1, (maxWidth / 44.dp).toInt())
        FlowRow(modifier = Modifier.fillMaxWidth(), maxItemsInEachRow = columns) {
            IconCell(
                modifier = Modifier.weight(1f),
                selected = icon == null,
                label = stringResource(R.string.appearance_icon_none),
                onClick = {
                    icon = null
                    emojiText = ""
                    iconTouched = true
                },
            ) {
                Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            LucideIconSet.forEach { cell ->
                IconCell(
                    modifier = Modifier.weight(1f),
                    selected = (icon as? EntityIcon.Vector)?.key == cell.key,
                    label = stringResource(iconLabelRes(cell.key)),
                    onClick = {
                        icon = cell
                        emojiText = ""
                        iconTouched = true
                    },
                ) {
                    EntityIconView(cell, tint = iconTint, size = 22.dp)
                }
            }
            repeat((columns - (LucideIconSet.size + 1) % columns) % columns) {
                Spacer(Modifier.weight(1f).height(44.dp))
            }
        }
        }
        FloatingCapsule(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TesseraTextField(
                value = emojiText,
                onValueChange = { typed ->
                    if (typed.isEmpty()) {
                        emojiText = ""
                        if (icon is EntityIcon.Emoji) {
                            icon = null
                            iconTouched = true
                        }
                    } else {
                        // Several graphemes: the last one wins. One that is not an emoji changes nothing,
                        // and the field snaps back to the previous value.
                        val last = lastGrapheme(typed)
                        if (isAcceptableEmoji(last)) {
                            emojiText = last
                            icon = EntityIcon.Emoji(last)
                            iconTouched = true
                        }
                    }
                },
                placeholder = stringResource(R.string.appearance_emoji),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp),
            )
        }

        if (name == null) {
            TagPreview(tag = title.removePrefix("#"), color = color, icon = icon)
        } else {
            FolderPreview(name = nameText.trim(), color = color, icon = icon)
        }

    }
}

@Composable
private fun SectionLabel(@StringRes res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ColorDot(accent: AccentColor, selected: Boolean, onClick: () -> Unit) {
    val colors = accent.chipColors()
    DotSlot(selected = selected, label = stringResource(colorLabelRes(accent)), onClick = onClick) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.container)
                .border(BorderStroke(1.5.dp, colors.content), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.content, modifier = Modifier.size(18.dp))
        }
    }
}

/** "No colour": an outlined dot with a diagonal. */
@Composable
private fun NoColorDot(selected: Boolean, label: String, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    DotSlot(selected = selected, label = label, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(BorderStroke(1.5.dp, outline), CircleShape)
                .drawBehind {
                    val inset = size.minDimension * 0.22f
                    drawLine(
                        color = outline,
                        start = Offset(inset, size.height - inset),
                        end = Offset(size.width - inset, inset),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DotSlot(selected: Boolean, label: String, onClick: () -> Unit, dot: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { dot() }
}

@Composable
private fun IconCell(modifier: Modifier, selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = modifier.height(44.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}


/** The tag chip as it will look in the filter row; no colour means the hash colour, whatever is stored now. */
@Composable
private fun TagPreview(tag: String, color: AccentColor?, icon: EntityIcon?) {
    CompositionLocalProvider(LocalTagAppearances provides emptyMap()) {
        val colors: TagChipColors = color?.chipColors() ?: rememberTagChipColors(tag)
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.container)
                .heightIn(min = 32.dp)
                .padding(start = if (icon != null) TesseraSpacing.sm else TesseraSpacing.lg, end = TesseraSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.sm),
        ) {
            if (icon != null) EntityIconView(icon, tint = colors.content, size = 16.dp)
            Text(
                text = if (icon != null) tag else "#$tag",
                style = MaterialTheme.typography.labelLarge,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The folder row as it will look in the drawer. */
@Composable
private fun FolderPreview(name: String, color: AccentColor?, icon: EntityIcon?) {
    val tint = color?.chipColors()?.content ?: MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TesseraSpacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
        ) {
            if (icon != null) {
                EntityIconView(icon, tint = tint, size = 18.dp)
            } else {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(
                text = name.ifEmpty { stringResource(R.string.folders_new_hint) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (name.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The last user-perceived character of [text] (a ZWJ family or a flag is one). */
private fun lastGrapheme(text: String): String {
    val it = BreakIterator.getCharacterInstance()
    it.setText(text)
    val end = it.last()
    val start = it.previous()
    return if (start == BreakIterator.DONE) text else text.substring(start, end)
}

@StringRes
private fun colorLabelRes(accent: AccentColor): Int = when (accent) {
    AccentColor.Red -> R.string.color_red
    AccentColor.Orange -> R.string.color_orange
    AccentColor.Yellow -> R.string.color_yellow
    AccentColor.Green -> R.string.color_green
    AccentColor.Teal -> R.string.color_teal
    AccentColor.Blue -> R.string.color_blue
    AccentColor.Indigo -> R.string.color_indigo
    AccentColor.Purple -> R.string.color_purple
    AccentColor.Pink -> R.string.color_pink
    AccentColor.Brown -> R.string.color_brown
}

/** `contentDescription` of a Lucide cell, by its stored key. */
@StringRes
private fun iconLabelRes(key: String): Int = when (key) {
    "folder" -> R.string.icon_folder
    "house" -> R.string.icon_house
    "briefcase" -> R.string.icon_briefcase
    "book-open" -> R.string.icon_book_open
    "lightbulb" -> R.string.icon_lightbulb
    "star" -> R.string.icon_star
    "heart" -> R.string.icon_heart
    "shopping-cart" -> R.string.icon_shopping_cart
    "plane" -> R.string.icon_plane
    "car" -> R.string.icon_car
    "dumbbell" -> R.string.icon_dumbbell
    "utensils" -> R.string.icon_utensils
    "coffee" -> R.string.icon_coffee
    "music" -> R.string.icon_music
    "camera" -> R.string.icon_camera
    "gamepad-2" -> R.string.icon_gamepad_2
    "graduation-cap" -> R.string.icon_graduation_cap
    "code" -> R.string.icon_code
    "palette" -> R.string.icon_palette
    "wallet" -> R.string.icon_wallet
    "calendar" -> R.string.icon_calendar
    "clock" -> R.string.icon_clock
    "flag" -> R.string.icon_flag
    "bookmark" -> R.string.icon_bookmark
    "tag" -> R.string.icon_tag
    "users" -> R.string.icon_users
    "baby" -> R.string.icon_baby
    "paw-print" -> R.string.icon_paw_print
    "leaf" -> R.string.icon_leaf
    else -> R.string.icon_gift
}
