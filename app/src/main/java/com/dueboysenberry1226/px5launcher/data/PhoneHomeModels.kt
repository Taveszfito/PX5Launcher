package com.dueboysenberry1226.px5launcher.data

enum class PhoneCardType {
    CALENDAR,
    MUSIC,
    NOTIFICATIONS
}

data class PhoneCardPlacement(
    val type: PhoneCardType,
    val row: Int,
    val col: Int
)

object PhoneHomeCodec {
    // cards: "CALENDAR,0,0;MUSIC,2,0"
    fun encodeCards(cards: List<PhoneCardPlacement>): String {
        return cards.joinToString(";") { c ->
            "${c.type.name},${c.row},${c.col}"
        }
    }

    fun decodeCards(raw: String?): List<PhoneCardPlacement> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";")
            .mapNotNull { token ->
                val p = token.split(",")
                if (p.size != 3) return@mapNotNull null
                val type = runCatching { PhoneCardType.valueOf(p[0]) }.getOrNull() ?: return@mapNotNull null
                val row = p[1].toIntOrNull() ?: return@mapNotNull null
                val col = p[2].toIntOrNull() ?: return@mapNotNull null
                PhoneCardPlacement(type, row, col)
            }
    }

    // slots: string list null-okkal -> "\u0001" szeparátorral, üres = null
    private const val SEP = "\u0001"

    fun encodeSlots(slots: List<String?>): String =
        slots.joinToString(SEP) { it?.trim().orEmpty() }

    fun decodeSlots(raw: String?): List<String?> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(SEP).map { v ->
            val t = v.trim()
            if (t.isBlank()) null else t
        }
    }
}