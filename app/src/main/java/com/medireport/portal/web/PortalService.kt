package com.medireport.portal.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.medireport.portal.App
import com.medireport.portal.MainActivity
import com.medireport.portal.R
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

data class PortalState(val running: Boolean = false, val port: Int = 0, val error: String? = null)

/** Keeps the patient web portal running in the background (foreground service with a notification). */
class PortalService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: PortalServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val n = notification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)

        scope.launch {
            val repo = (application as App).repository
            val port = repo.prefs.getPortal().port
            stopServer()
            try {
                val s = PortalServer(repo, port)
                s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                server = s
                _state.value = PortalState(true, port, null)
            } catch (e: Exception) {
                _state.value = PortalState(false, port, e.message ?: "Could not start the server")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopServer() {
        runCatching { server?.stop() }
        server = null
        _state.value = PortalState(false, _state.value.port, null)
    }

    override fun onDestroy() {
        stopServer()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, getString(R.string.portal_channel), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PortalService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.portal_notif_title))
            .setContentText(getString(R.string.portal_notif_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.portal_stop), stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "portal"
        private const val NOTIF_ID = 4711
        const val ACTION_STOP = "com.medireport.portal.STOP_PORTAL"

        private val _state = MutableStateFlow(PortalState())
        val state: StateFlow<PortalState> = _state

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PortalService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PortalService::class.java).setAction(ACTION_STOP))
        }

        /** Local network addresses patients can use (same Wi-Fi / hotspot). */
        fun localAddresses(): List<String> = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .map { it.hostAddress ?: "" }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
