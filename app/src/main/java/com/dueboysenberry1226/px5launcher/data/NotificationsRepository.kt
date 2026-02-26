package com.dueboysenberry1226.px5launcher.data

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dueboysenberry1226.px5launcher.ui.PX5NotificationItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

object NotificationsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _live = MutableStateFlow<List<PX5NotificationItem>>(emptyList())
    val live: StateFlow<List<PX5NotificationItem>> = _live.asStateFlow()

    private val _history = MutableStateFlow<List<PX5NotificationItem>>(emptyList())
    val history: StateFlow<List<PX5NotificationItem>> = _history.asStateFlow()

    private val KEY_HISTORY = stringPreferencesKey("notif_history_v1")

    private var appContext: Context? = null

    // ✅ memory leak fix: ne tartsunk erős referenciát Service-re statikusan
    private var serviceRef: WeakReference<NotificationListenerService>? = null
    private fun service(): NotificationListenerService? = serviceRef?.get()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        // history betöltés
        scope.launch {
            context.px5DataStore.data
                .map { prefs -> prefs[KEY_HISTORY].orEmpty() }
                .distinctUntilChanged()
                .map { decodeHistory(it) }
                .catch { emit(emptyList()) }
                .collect { _history.value = it }
        }
    }

    fun onListenerConnected(nls: NotificationListenerService) {
        serviceRef = WeakReference(nls)
        val ctx = appContext ?: nls.applicationContext
        if (appContext == null) init(ctx)

        val current = nls.activeNotifications
            ?.mapNotNull { sbn -> sbnToItem(ctx, sbn) }
            .orEmpty()

        _live.value = current.sortedByDescending { it.postedAtMillis }
    }

    fun onListenerDisconnected() {
        serviceRef = null
        _live.value = emptyList()
    }

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val ctx = appContext ?: return
        val item = sbnToItem(ctx, sbn) ?: return

        val list = _live.value.toMutableList()
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(0, item)
        _live.value = list.sortedByDescending { it.postedAtMillis }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        val id = sbn.key
        _live.value = _live.value.filterNot { it.id == id }
    }

    /**
     * Értesítés "megnyitása" (Enter/OK).
     * 1) contentIntent.send()
     * 2) fallback: app indítása package alapján
     */
    fun launch(id: String, fromHistory: Boolean = false): Boolean {
        val ctx = appContext ?: return false
        val item = (if (fromHistory) _history.value else _live.value)
            .firstOrNull { it.id == id } ?: return false

        fun toastNoAction() {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    ctx,
                    "Ehhez az értesítéshez nincs megnyitható művelet.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // 1) PendingIntent (live-on; history-ban jellemzően nincs eltárolva)
        if (!fromHistory) {
            val pi = item.contentIntent
            if (pi != null) {
                val ok = runCatching { pi.send() }.isSuccess
                if (ok) return true
            }
        }

        // 2) App indítás (ha van launcher activity)
        val pkg = item.packageName
        if (pkg.isNotBlank()) {
            val launchIntent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (launchIntent != null) {
                val ok = runCatching { ctx.startActivity(launchIntent) }.isSuccess
                if (ok) return true
            }

            // 3) App info fallback
            val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val okInfo = runCatching { ctx.startActivity(appInfo) }.isSuccess
            if (okInfo) return true
        }

        // 4) Semmi nem nyitható
        toastNoAction()
        return false
    }

    fun dismissLiveToHistory(id: String) {
        val item = _live.value.firstOrNull { it.id == id } ?: return
        _live.value = _live.value.filterNot { it.id == id }

        runCatching { service()?.cancelNotification(id) }

        // history-ba intent nélkül
        addToHistory(item.copy(contentIntent = null))
    }

    fun removeFromHistory(id: String) {
        val next = _history.value.filterNot { it.id == id }
        scope.launch {
            val ctx = appContext ?: return@launch
            ctx.px5DataStore.edit { it[KEY_HISTORY] = encodeHistory(next) }
        }
    }

    fun clearHistory() {
        scope.launch {
            val ctx = appContext ?: return@launch
            ctx.px5DataStore.edit { it[KEY_HISTORY] = "" }
        }
    }

    fun clearLiveToHistory() {
        val items = _live.value
        _live.value = emptyList()

        items.forEach { i -> runCatching { service()?.cancelNotification(i.id) } }

        addManyToHistory(items.map { it.copy(contentIntent = null) })
    }

    private fun addToHistory(item: PX5NotificationItem) {
        addManyToHistory(listOf(item))
    }

    private fun addManyToHistory(items: List<PX5NotificationItem>) {
        if (items.isEmpty()) return
        val existing = _history.value.toMutableList()

        for (i in items) {
            if (existing.none { it.id == i.id }) existing.add(0, i)
        }

        scope.launch {
            val ctx = appContext ?: return@launch
            ctx.px5DataStore.edit { it[KEY_HISTORY] = encodeHistory(existing) }
        }
    }

    private fun sbnToItem(ctx: Context, sbn: StatusBarNotification): PX5NotificationItem? {
        val n = sbn.notification ?: return null

        // ❗ FONTOS: ne dobjuk el az ongoingokat, különben a Cast/sok rendszer notif nem lesz launcholható
        // (ha később akarod szűrni, csinálunk whitelist/blacklistet)

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

        val msg = when {
            text.isNotBlank() && title.isNotBlank() -> "$title • $text"
            text.isNotBlank() -> text
            title.isNotBlank() -> title
            subText.isNotBlank() -> subText
            else -> "Értesítés"
        }

        val pm = ctx.packageManager
        val appName = try {
            val ai = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(ai).toString().trim().ifBlank { sbn.packageName }
        } catch (_: PackageManager.NameNotFoundException) {
            sbn.packageName
        }

        val openIntent = n.contentIntent ?: n.fullScreenIntent

        return PX5NotificationItem(
            id = sbn.key,
            appName = appName,
            message = msg,
            postedAtMillis = sbn.postTime,
            packageName = sbn.packageName,
            contentIntent = openIntent
        )
    }

    private fun encodeHistory(list: List<PX5NotificationItem>): String {
        val arr = JSONArray()
        for (i in list) {
            val o = JSONObject()
            o.put("id", i.id)
            o.put("appName", i.appName)
            o.put("message", i.message)
            o.put("postedAt", i.postedAtMillis)
            o.put("pkg", i.packageName)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun decodeHistory(raw: String): List<PX5NotificationItem> {
        if (raw.isBlank()) return emptyList()
        val arr = JSONArray(raw)
        val out = ArrayList<PX5NotificationItem>(arr.length())
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            out.add(
                PX5NotificationItem(
                    id = o.optString("id"),
                    appName = o.optString("appName"),
                    message = o.optString("message"),
                    postedAtMillis = o.optLong("postedAt"),
                    packageName = o.optString("pkg"),
                    contentIntent = null
                )
            )
        }
        return out
    }
}