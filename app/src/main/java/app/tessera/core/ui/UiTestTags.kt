package app.tessera.core.ui

object UiTestTags {
    const val ShowcaseRoot = "showcase_root"
    const val TopCapsule = "top_capsule"
    const val ShowcaseCard = "showcase_card"
    const val BottomCapsule = "bottom_capsule"
    const val CreateNoteFab = "create_note_fab"
    const val NotesListRoot = "notes_list_root"
    const val NotesGrid = "notes_grid"
    const val SearchField = "search_field"
    const val SearchClear = "search_clear"
    const val TagFilterToggle = "tag_filter_toggle"
    const val ViewModeToggle = "view_mode_toggle"

    fun tagFilterChip(name: String): String = "tag_filter_chip_$name"

    // Editor screen
    const val EditorRoot = "editor_root"
    const val EditorTitleField = "editor_title_field"
    const val EditorBodyField = "editor_body_field"
    const val EditorBack = "editor_back"
    const val EditorPin = "editor_pin"
    const val EditorDelete = "editor_delete"
    const val EditorOverflow = "editor_overflow"
    const val EditorTagToggle = "editor_tag_toggle"
    const val EditorFormatMenuTrigger = "editor_format_menu_trigger"

    // Settings screen
    const val OpenSettings = "open_settings"
    const val SettingsRoot = "settings_root"
    const val SettingsBack = "settings_back"
    const val SettingsSignIn = "settings_sign_in"
    const val SettingsSignOut = "settings_sign_out"
    const val SettingsAppearanceRow = "settings_appearance_row"
    const val SettingsLicensesRow = "settings_licenses_row"
    const val AppearanceRoot = "appearance_root"
    const val AppearanceBack = "appearance_back"
    const val AppearanceThemeRow = "appearance_theme_row"
    const val AppearanceGradientRow = "appearance_gradient_row"
    const val AppearanceMotionRow = "appearance_motion_row"
    const val AppearanceGrainRow = "appearance_grain_row"
    const val LicensesRoot = "licenses_root"
    const val LicensesBack = "licenses_back"
    const val LicensesText = "licenses_text"
    const val AccountSwitchDialog = "account_switch_dialog"
    const val AccountSwitchMove = "account_switch_move"
    const val AccountSwitchDelete = "account_switch_delete"

    const val ReusableTextField = "reusable_text_field"

    const val PopupScrim = "popup_scrim"
    const val PopupCard = "popup_card"

    const val AddTagConfirm = "add_tag_confirm"

    fun noteCard(noteId: String): String = "note_card_$noteId"

    fun swipeAction(noteId: String, key: String): String = "swipe_action_${key}_$noteId"

    fun sectionHeader(group: String): String = "section_header_$group"
}
