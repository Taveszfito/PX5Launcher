package com.dueboysenberry1226.px5launcher.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dueboysenberry1226.px5launcher.data.NotificationsRepository

class PX5NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaControllerRepo.onListenerConnected(this)

        NotificationsRepository.onListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MediaControllerRepo.onListenerDisconnected()

        NotificationsRepository.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        NotificationsRepository.onNotificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        NotificationsRepository.onNotificationRemoved(sbn)
    }
}