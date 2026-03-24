package com.dueboysenberry1226.px5launcher.data

enum class PhoneCardType {
    CALENDAR,
    MUSIC,
    NOTIFICATIONS
}

data class PhoneCardPlacement(
    val type: PhoneCardType,
    val row: Int,
    val col: Int,
    val pageIndex: Int = 0
)

object PhoneHomeCodec {
    // cards v2: "CALENDAR,0,0,0;MUSIC,2,0,1"
    // régi v1 is támogatott: "CALENDAR,0,0;MUSIC,2,0"
    fun encodeCards(cards: List<PhoneCardPlacement>): String {
        return cards.joinToString(";") { c ->
            "${c.type.name},${c.row},${c.col},${c.pageIndex}"
        }
    }

    fun decodeCards(raw: String?): List<PhoneCardPlacement> {
        if (raw.isNullOrBlank()) return emptyList()

        return raw.split(";")
            .mapNotNull { token ->
                val p = token.split(",")

                when (p.size) {
                    3 -> {
                        val type = runCatching { PhoneCardType.valueOf(p[0]) }.getOrNull()
                            ?: return@mapNotNull null
                        val row = p[1].toIntOrNull() ?: return@mapNotNull null
                        val col = p[2].toIntOrNull() ?: return@mapNotNull null
                        PhoneCardPlacement(
                            type = type,
                            row = row,
                            col = col,
                            pageIndex = 0
                        )
                    }

                    4 -> {
                        val type = runCatching { PhoneCardType.valueOf(p[0]) }.getOrNull()
                            ?: return@mapNotNull null
                        val row = p[1].toIntOrNull() ?: return@mapNotNull null
                        val col = p[2].toIntOrNull() ?: return@mapNotNull null
                        val pageIndex = p[3].toIntOrNull() ?: return@mapNotNull null
                        PhoneCardPlacement(
                            type = type,
                            row = row,
                            col = col,
                            pageIndex = pageIndex.coerceAtLeast(0)
                        )
                    }

                    else -> null
                }
            }
    }

    // slots v2:
    // page0slot0<SEP>page0slot1...<PAGESEP>page1slot0...
    // régi v1 mentés fallback: egyetlen oldal
    private const val SEP = "\u0001"
    private const val PAGE_SEP = "\u0002"

    fun encodeSlotsPages(pages: List<List<String?>>): String {
        return pages.joinToString(PAGE_SEP) { page ->
            page.joinToString(SEP) { it?.trim().orEmpty() }
        }
    }

    fun decodeSlotsPages(raw: String?): List<List<String?>> {
        if (raw.isNullOrBlank()) return emptyList()

        return if (PAGE_SEP in raw) {
            raw.split(PAGE_SEP).map { pageRaw ->
                pageRaw.split(SEP).map { v ->
                    val t = v.trim()
                    t.ifBlank { null }
                }
            }
        } else {
            listOf(
                raw.split(SEP).map { v ->
                    val t = v.trim()
                    t.ifBlank { null }
                }
            )
        }
    }

    // kompatibilitási wrapper a régi hívásokhoz
    fun encodeSlots(slots: List<String?>): String =
        encodeSlotsPages(listOf(slots))

    fun decodeSlots(raw: String?): List<String?> =
        decodeSlotsPages(raw).firstOrNull().orEmpty()
}