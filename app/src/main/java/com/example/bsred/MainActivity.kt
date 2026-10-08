package com.example.bsred

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var permissionReply: Pair<String, JavaScriptReplyProxy>? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionReply?.let { (id, proxy) -> replyPermissions(id, proxy) }
        permissionReply = null
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun gpsEnabled(): Boolean {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun permissions() = buildList {
        if (!hasLocation()) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private fun replyPermissions(id: String, proxy: JavaScriptReplyProxy) {
        proxy.postMessage(JSONObject().put("request_id", id).put("success", true)
            .put("autorizado", hasLocation()).put("gps_habilitado", gpsEnabled()).toString())
    }

    private fun trusted(uri: Uri) = TrackingPolicy.trustedAddress(uri.toString())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (trusted(request.url)) return false
                if (request.isForMainFrame && request.url.scheme in listOf("https", "http")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (_: Exception) { }
                }
                return true
            }
        }
        // Sólo el origen propio y el marco principal pueden usar el servicio nativo.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "BSRedAndroid", setOf("https://bsred.onrender.com")) { _, message, origin, mainFrame, proxy ->
                if (!mainFrame || !trusted(origin) || web.url?.let { trusted(Uri.parse(it)) } != true) return@addWebMessageListener
                val data = try { JSONObject(message.data ?: "") } catch (_: Exception) { return@addWebMessageListener }
                val id = data.optString("request_id").take(32)
                val response = JSONObject().put("request_id", id)
                try {
                    when (data.optString("action")) {
                        "permisos" -> { replyPermissions(id, proxy); return@addWebMessageListener }
                        "solicitarPermisos" -> {
                            val requested = permissions()
                            if (requested.isEmpty()) replyPermissions(id, proxy)
                            else if (permissionReply == null) { permissionReply = id to proxy; permissionLauncher.launch(requested) }
                            else proxy.postMessage(response.put("success", false).put("message", "Responde primero al permiso del dispositivo.").toString())
                            return@addWebMessageListener
                        }
                        "iniciar" -> {
                            val trip = data.optLong("viaje_id")
                            val token = data.optString("token")
                            require(hasLocation() && gpsEnabled()) { "Permite la ubicación precisa y activa el GPS antes de iniciar." }
                            require(TrackingPolicy.validJourney(trip, token)) { "Credencial de viaje inválida." }
                            ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java)
                                .putExtra("viaje_id", trip).putExtra("token", token))
                        }
                        "detener" -> {
                            getSharedPreferences("viaje", MODE_PRIVATE).edit().clear().apply()
                            stopService(Intent(this, TrackingService::class.java))
                        }
                        else -> error("Operación no admitida.")
                    }
                    response.put("success", true)
                } catch (_: Exception) {
                    response.put("success", false).put("message", "No se pudo activar la ubicación. Revisa los permisos y reintenta con la app abierta.")
                }
                proxy.postMessage(response.toString())
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
        })
        web.loadUrl("https://bsred.onrender.com")
        val requested = permissions()
        if (requested.isNotEmpty()) permissionLauncher.launch(requested)
    }

    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
