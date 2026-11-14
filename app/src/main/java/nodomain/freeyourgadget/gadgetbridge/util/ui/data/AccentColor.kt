package nodomain.freeyourgadget.gadgetbridge.util.ui.data

/**
 * The accent color presets that the user can select, in the order that they are offered.
 *
 * @property value the stored preference value
 */
enum class AccentColor(val value: String) {
    RED("red"),
    CORAL("coral"),
    ORANGE("orange"),
    AMBER("amber"),
    LIME("lime"),
    GREEN("green"),
    TEAL("teal"),
    CYAN("cyan"),
    BLUE("blue"),
    INDIGO("indigo"),
    VIOLET("violet"),
    PINK("pink"),
    WHITE("white");

    /**
     * White needs a dark backdrop to read against, so it is only offered with the dark theme.
     */
    fun isAvailable(dark: Boolean): Boolean = this != WHITE || dark

    companion object {
        @JvmField
        val DEFAULT = RED

        /**
         * The accent color stored as [value]. Missing or unknown values fall back to [DEFAULT].
         */
        @JvmStatic
        fun fromValue(value: String?): AccentColor = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}
