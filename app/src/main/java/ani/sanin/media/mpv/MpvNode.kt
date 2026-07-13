package ani.sanin.media.mpv

sealed class MpvNode {
    object None : MpvNode()
    data class StringNode(val value: String) : MpvNode()
    data class BooleanNode(val value: Boolean) : MpvNode()
    data class IntNode(val value: Long) : MpvNode()
    data class DoubleNode(val value: Double) : MpvNode()
    data class ArrayNode(val value: Array<MpvNode>) : MpvNode()
    data class MapNode(val value: Map<String, MpvNode>) : MpvNode()

    fun asString(): String? = (this as? StringNode)?.value
    fun asBoolean(): Boolean? = (this as? BooleanNode)?.value
    fun asInt(): Long? = (this as? IntNode)?.value
    fun asDouble(): Double? = (this as? DoubleNode)?.value
    fun asArray(): Array<MpvNode>? = (this as? ArrayNode)?.value
    fun asMap(): Map<String, MpvNode>? = (this as? MapNode)?.value
    operator fun get(index: Int): MpvNode? = asArray()?.getOrNull(index)
    operator fun get(key: String): MpvNode? = asMap()?.get(key)
}
