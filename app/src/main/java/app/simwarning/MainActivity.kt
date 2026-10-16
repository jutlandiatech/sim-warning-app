package app.simwarning

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import app.simwarning.core.AppController
import app.simwarning.platform.*
import app.simwarning.ui.AppRoot

class MainActivity : ComponentActivity() {
    private var refreshToken by mutableIntStateOf(0)
    private var openMainToken by mutableIntStateOf(0)
    private lateinit var notifications: NotificationCenter
    private val requests = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshToken++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notifications = NotificationCenter(this)
        setContent { AppRoot(refreshToken, openMainToken, ::reviewPermissions, ::openSettings, notifications) }
    }
    private fun reviewPermissions() {
        val missing = AndroidAccess(this).requiredRuntimePermissions()
        val history = getSharedPreferences("permission_requests", MODE_PRIVATE)
        if (missing.isEmpty() || missing.any { history.getBoolean(it, false) && !shouldShowRequestPermissionRationale(it) }) { openSettings(); return }
        val editor = history.edit()
        missing.forEach { editor.putBoolean(it, true) }
        editor.apply()
        requests.launch(missing)
    }
    private fun openSettings() { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    override fun onResume() {
        super.onResume()
        refreshToken++
        if (!AndroidAccess(this).ready()) AndroidMonitorPlatform(this).stop()
        else AppController(AppPreferences(this), AndroidMonitorPlatform(this)).resumeIfEnabled()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); openMainToken++ }
    override fun onStop() { if (::notifications.isInitialized) notifications.stopPreview(); super.onStop() }
}
