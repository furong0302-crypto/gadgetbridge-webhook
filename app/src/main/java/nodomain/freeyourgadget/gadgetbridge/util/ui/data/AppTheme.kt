package nodomain.freeyourgadget.gadgetbridge.util.ui.data

/**
 * The themes that the user can select, in the order that they are offered.
 *
 * @property value the stored preference value, which must match `R.array.pref_theme_values`
 */
enum class AppTheme(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    DYNAMIC("dynamic");

    companion object {
        /**
         * The theme stored as [value]. Missing or unknown values follow the system.
         */
        @JvmStatic
        fun fromValue(value: String?): AppTheme = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}
