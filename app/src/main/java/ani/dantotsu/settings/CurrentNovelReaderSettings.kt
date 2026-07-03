package ani.dantotsu.settings

class CurrentNovelReaderSettings {
    var layout: Layouts = Layouts.PAGED

    enum class Layouts(val string: String) {
        PAGED("Paged"),
        VERTICAL("Vertical"),
        CONTINOUS("Continuous");

        companion object {
            operator fun get(index: Int): Layouts = entries.getOrNull(index) ?: PAGED
        }
    }

    var dualPageMode: CurrentReaderSettings.DualPageModes = CurrentReaderSettings.DualPageModes.Automatic
    var lineHeight: Float = 1.4f
    var margin: Float = 0.06f
    var maxInlineSize: Int = 60
}
