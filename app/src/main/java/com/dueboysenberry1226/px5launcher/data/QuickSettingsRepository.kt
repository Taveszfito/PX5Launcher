package com.dueboysenberry1226.px5launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dueboysenberry1226.px5launcher.ui.QuickTileType
import com.dueboysenberry1226.px5launcher.ui.QuickTilesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

object QuickSettingsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val KEY_QS = stringPreferencesKey("quick_tiles_v1")

    private val _state = MutableStateFlow(QuickTilesState())
    val state: StateFlow<QuickTilesState> = _state.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        scope.launch {
            context.px5DataStore.data
                .map { it[KEY_QS].orEmpty() }
                .distinctUntilChanged()
                .map { decode(it) }
                .catch { emit(QuickTilesState()) }
                .collect { _state.value = it }
        }
    }

    fun assign(slot: Int, type: QuickTileType) {
        val current = _state.value.slots.toMutableList()
        if (slot !in 0 until 20) return
        current[slot] = type
        persist(current)
    }

    fun remove(slot: Int) {
        val current = _state.value.slots.toMutableList()
        if (slot !in 0 until 20) return
        current[slot] = null
        persist(current)
    }

    private fun persist(slots: List<QuickTileType?>) {
        val next = QuickTilesState(slots = slots)
        _state.value = next
        scope.launch {
            val ctx = appContext ?: return@launch
            ctx.px5DataStore.edit { it[KEY_QS] = encode(slots) }
        }
    }

    private fun encode(slots: List<QuickTileType?>): String {
        // 20 elem, üres = "", többi = enum name
        return slots.joinToString("|") { it?.name.orEmpty() }
    }

    private fun decode(raw: String): QuickTilesState {
        if (raw.isBlank()) return QuickTilesState()
        val parts = raw.split("|")
        val out = MutableList<QuickTileType?>(20) { null }
        for (i in 0 until minOf(20, parts.size)) {
            val p = parts[i]
            out[i] = if (p.isBlank()) null else runCatching { QuickTileType.valueOf(p) }.getOrNull()
        }
        return QuickTilesState(slots = out)
    }
}