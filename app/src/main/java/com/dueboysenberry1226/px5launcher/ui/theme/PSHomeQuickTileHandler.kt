@file:OptIn(ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.theme

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.dueboysenberry1226.px5launcher.R

@Composable
internal fun rememberQuickTileClickHandler(
    context: Context
): (QuickTileType) -> Unit {
    var torchOn by remember { mutableStateOf(false) }
    var pendingAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun log(msg: String) {
        Log.d("PX5QS", msg)
    }

    fun openIntent(intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openInternetPanel() {
        openIntent(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
    }

    fun openBluetoothSettings() {
        openIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun openDndAccessSettings() {
        openIntent(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    fun openDisplaySettings() {
        openIntent(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }

    fun openSystemSettings() {
        openIntent(Intent(Settings.ACTION_SETTINGS))
    }

    fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        log("CAMERA permission result: $granted")
        if (granted) {
            pendingAfterPermission?.invoke()
        } else {
            openAppDetailsSettings()
        }
        pendingAfterPermission = null
    }

    val requestBtConnect = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        log("BT_CONNECT permission result: $granted")
        if (granted) {
            pendingAfterPermission?.invoke()
        } else {
            openAppDetailsSettings()
        }
        pendingAfterPermission = null
    }

    fun toggleBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            openBluetoothSettings()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perm = Manifest.permission.BLUETOOTH_CONNECT
            val granted = ContextCompat.checkSelfPermission(
                context,
                perm
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                pendingAfterPermission = { toggleBluetooth() }
                requestBtConnect.launch(perm)
                return
            }
        }

        val ok = runCatching {
            if (adapter.isEnabled) adapter.disable() else adapter.enable()
        }.getOrNull() == true

        if (!ok) openBluetoothSettings()
    }

    fun toggleFlashlightInternal() {
        val pm = context.packageManager
        val hasFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        log("toggleFlashlightInternal hasFlash=$hasFlash")

        if (!hasFlash) {
            toast(context.getString(R.string.homescreen_qs_no_flashlight))
            return
        }

        val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraId = runCatching {
            cam.cameraIdList.firstOrNull { id ->
                val ch = cam.getCameraCharacteristics(id)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()

        log("toggleFlashlightInternal cameraId=$cameraId")

        if (cameraId == null) {
            toast(context.getString(R.string.homescreen_qs_no_flash_camera))
            return
        }

        val next = !torchOn
        val ok = runCatching {
            cam.setTorchMode(cameraId, next)
            torchOn = next
        }.isSuccess

        log("toggleFlashlightInternal setTorchMode next=$next ok=$ok")

        if (!ok) {
            toast(context.getString(R.string.homescreen_qs_flashlight_toggle_failed))
        }
    }

    fun toggleFlashlight() {
        val perm = Manifest.permission.CAMERA
        val granted = ContextCompat.checkSelfPermission(
            context,
            perm
        ) == PackageManager.PERMISSION_GRANTED

        log("toggleFlashlight permissionGranted=$granted")

        if (!granted) {
            pendingAfterPermission = { toggleFlashlightInternal() }
            requestCamera.launch(perm)
            return
        }

        toggleFlashlightInternal()
    }

    fun toggleDnd() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!nm.isNotificationPolicyAccessGranted) {
            openDndAccessSettings()
            return
        }

        val cur = nm.currentInterruptionFilter
        val next = if (cur == NotificationManager.INTERRUPTION_FILTER_NONE) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            NotificationManager.INTERRUPTION_FILTER_NONE
        }

        runCatching { nm.setInterruptionFilter(next) }
            .onFailure { openDndAccessSettings() }
    }

    return remember {
        { type: QuickTileType ->
            log("Tile click: $type")
            when (type) {
                QuickTileType.WIFI -> openInternetPanel()
                QuickTileType.BT -> toggleBluetooth()
                QuickTileType.FLASHLIGHT -> toggleFlashlight()
                QuickTileType.DND -> toggleDnd()
                QuickTileType.ROTATION -> openDisplaySettings()
                QuickTileType.AIRPLANE -> openSystemSettings()
                QuickTileType.LOCATION -> openIntent(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                QuickTileType.STB -> openSystemSettings()
            }
        }
    }
}