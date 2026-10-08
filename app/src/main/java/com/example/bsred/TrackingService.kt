package com.example.bsred

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

class TrackingService : Service(), LocationListener {
    private data class Journey(val id: Long, val token: String)
    @Volatile private var journey: Journey? = null
    @Volatile private var latest: Location? = null
    private lateinit var manager: LocationManager
    private val worker = Executors.newSingleThreadScheduledExecutor()

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LOCATION_SERVICE) as LocationManager
        worker.scheduleWithFixedDelay({ transmit() }, 2, 15, TimeUnit.SECONDS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val saved = getSharedPreferences("viaje", MODE_PRIVATE)
        val id = intent?.getLongExtra("viaje_id", 0) ?: saved.getLong("id", 0)
        val token = intent?.getStringExtra("token") ?: saved.getString("token", "") ?: ""
        if (!TrackingPolicy.validJourney(id, token)) { stopSelf(); return START_NOT_STICKY }
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine) { terminate(); return START_NOT_STICKY }
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(NotificationChannel("gps_viaje", "Ubicación durante el viaje", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "gps_viaje")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("BSRed: viaje en curso")
            .setContentText("Compartiendo ubicación. Finaliza el viaje al llegar al destino.")
            .setContentIntent(open).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(101, notification)
            journey = Journey(id, token)
            saved.edit().putLong("id", id).putString("token", token).apply()
            manager.removeUpdates(this)
            if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15000L, 0f, this, Looper.getMainLooper())
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 15000L, 0f, this, Looper.getMainLooper())
        } catch (_: SecurityException) { terminate(); return START_NOT_STICKY }
        catch (_: IllegalStateException) { terminate(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasAccuracy() && location.accuracy in 0f..2000f && (latest == null || location.time > latest!!.time)) latest = Location(location)
    }

    private fun transmit() {
        val active = journey ?: return
        val position = latest?.takeIf { TrackingPolicy.freshPosition(it.time, System.currentTimeMillis()) }
        var connection: HttpsURLConnection? = null
        try {
            val at = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(position?.time ?: 0))
            val bytes = position?.let { JSONObject().put("latitud", it.latitude).put("longitud", it.longitude)
                .put("precision_m", it.accuracy.toDouble()).put("ubicacion_en", at).toString().toByteArray(Charsets.UTF_8) }
            connection = URL("https://bsred.onrender.com/api/chofer/viajes/${active.id}/${if (position == null) "rastreo" else "ubicacion"}").openConnection() as HttpsURLConnection
            connection.requestMethod = if (position == null) "GET" else "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 60000
            connection.readTimeout = 15000
            connection.setRequestProperty("Authorization", "Bearer ${active.token}")
            connection.setRequestProperty("Content-Type", "application/json")
            if (bytes != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val result = connection.responseCode
            if (result in listOf(401, 403, 404, 410) && journey == active)
                Handler(Looper.getMainLooper()).post { if (journey == active) terminate() }
        } catch (_: Exception) { /* Se reintenta con la posición fresca; no registrar credenciales ni ubicaciones. */ }
        finally { connection?.disconnect() }
    }

    private fun terminate() { journey = null; getSharedPreferences("viaje", MODE_PRIVATE).edit().clear().apply(); stopSelf() }
    override fun onDestroy() { journey = null; worker.shutdownNow(); try { manager.removeUpdates(this) } catch (_: SecurityException) { }; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onProviderEnabled(provider: String) { }
    override fun onProviderDisabled(provider: String) { }
    @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }
}
