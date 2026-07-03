package ani.dantotsu.settings

class CurrentNovelReaderSettings {
    var layout: Layouts? = null

    enum class Layouts(val string: String) {
        PAGED("Paged"),
        VERTICAL("Vertical"),
        CONTINOUS("Continuous");

        companion object {
            operator fun get(index: Int): Layouts? = entries.getOrNull(index)
        }
    }
}
