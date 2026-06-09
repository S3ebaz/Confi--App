package com.confiapp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Puente Kotlin ↔ JavaScript.
 * Todos los métodos anotados con @JavascriptInterface son accesibles
 * desde el HTML como: Android.metodo()
 */
class AppBridge(private val context: Context) {

    // ── Permisos peligrosos (alto impacto en privacidad) ─────────────────────
    private val DANGEROUS = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.SEND_SMS",
        "android.permission.READ_PHONE_STATE",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.BODY_SENSORS",
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.GET_ACCOUNTS",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO"
    )

    // ── Permisos de riesgo medio ──────────────────────────────────────────────
    private val MEDIUM = setOf(
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.USE_BIOMETRIC",
        "android.permission.USE_FINGERPRINT",
        "android.permission.NFC",
        "android.permission.CHANGE_NETWORK_STATE"
    )

    // ── Nombres legibles para mostrar en la UI ────────────────────────────────
    private val LABEL = mapOf(
        "android.permission.CAMERA"                     to "Cámara",
        "android.permission.RECORD_AUDIO"               to "Micrófono",
        "android.permission.ACCESS_FINE_LOCATION"       to "Ubicación exacta",
        "android.permission.ACCESS_COARSE_LOCATION"     to "Ubicación aprox.",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "Ubicación 2do plano",
        "android.permission.READ_CONTACTS"              to "Leer contactos",
        "android.permission.WRITE_CONTACTS"             to "Modificar contactos",
        "android.permission.READ_CALL_LOG"              to "Historial llamadas",
        "android.permission.READ_SMS"                   to "Leer SMS",
        "android.permission.RECEIVE_SMS"                to "Recibir SMS",
        "android.permission.SEND_SMS"                   to "Enviar SMS",
        "android.permission.READ_PHONE_STATE"           to "Estado del teléfono",
        "android.permission.PROCESS_OUTGOING_CALLS"     to "Interceptar llamadas",
        "android.permission.BODY_SENSORS"               to "Sensores corporales",
        "android.permission.ACTIVITY_RECOGNITION"       to "Actividad física",
        "android.permission.GET_ACCOUNTS"               to "Cuentas del dispositivo",
        "android.permission.READ_CALENDAR"              to "Leer calendario",
        "android.permission.WRITE_CALENDAR"             to "Modificar calendario",
        "android.permission.READ_MEDIA_IMAGES"          to "Fotos",
        "android.permission.READ_MEDIA_VIDEO"           to "Videos",
        "android.permission.READ_MEDIA_AUDIO"           to "Audio",
        "android.permission.BLUETOOTH_CONNECT"          to "Bluetooth",
        "android.permission.NEARBY_WIFI_DEVICES"        to "Redes WiFi cercanas",
        "android.permission.USE_BIOMETRIC"              to "Biometría",
        "android.permission.NFC"                        to "NFC",
        "android.permission.INTERNET"                   to "Internet"
    )

    // ── Apps conocidas como de alto riesgo ────────────────────────────────────
    private val HIGH_RISK = mapOf(
        "com.zhiliaoapp.musically"  to "Accede al portapapeles sin avisar y recopila datos del dispositivo masivamente",
        "com.ss.android.ugc.trill"  to "Accede al portapapeles sin avisar y recopila datos del dispositivo masivamente",
        "com.facebook.katana"       to "Seguimiento entre apps y sitios web, reconocimiento facial activo",
        "com.facebook.mlite"        to "Seguimiento entre apps y sitios web",
        "com.truecaller"            to "Sube tu agenda completa a servidores externos sin opción de excluir contactos",
        "com.hiya.star"             to "Comparte historial de llamadas con socios comerciales",
        "com.cleanmaster.mguard"    to "Spyware documentado disfrazado de antivirus"
    )

    // ── Apps de confianza reconocida ──────────────────────────────────────────
    private val TRUSTED = setOf(
        "org.thoughtcrime.securesms",
        "com.onepassword.android",
        "com.bitwarden.mobile",
        "org.mozilla.firefox",
        "com.brave.browser",
        "net.mullvad.mullvadvpn",
        "com.protonvpn.android",
        "ch.protonmail.android"
    )

    // ═════════════════════════════════════════════════════════════════════════
    //  API pública — llamada desde JavaScript
    // ═════════════════════════════════════════════════════════════════════════

    /** Verifica que el puente está activo. JS: Android.ping() */
    @JavascriptInterface
    fun ping(): Boolean = true

    /** Versión de Android. JS: Android.getAndroidVersion() */
    @JavascriptInterface
    fun getAndroidVersion(): String = android.os.Build.VERSION.RELEASE

    /** Fabricante y modelo. JS: Android.getDeviceInfo() */
    @JavascriptInterface
    fun getDeviceInfo(): String =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    /**
     * Lee todas las apps de usuario instaladas y devuelve un JSON con
     * el análisis de riesgo de cada una.
     *
     * JS:
     *   const json = Android.getInstalledApps()
     *   const apps = JSON.parse(json)
     *   // apps[0].name, apps[0].risk, apps[0].score, etc.
     */
    @JavascriptInterface
    fun getInstalledApps(): String {
        val pm  = context.packageManager
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        return try {
            // GET_PERMISSIONS lee los permisos declarados en el AndroidManifest de cada app
            @Suppress("DEPRECATION")
            val packages: List<PackageInfo> =
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            val result = mutableListOf<JSONObject>()

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue

                // Saltar apps del sistema operativo
                if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

                // Saltar apps sin ícono en el cajón (servicios en segundo plano)
                if (pm.getLaunchIntentForPackage(pkg.packageName) == null) continue

                val allPerms   = pkg.requestedPermissions?.toList() ?: emptyList()
                val dangerous  = allPerms.filter { it in DANGEROUS }
                val medium     = allPerms.filter { it in MEDIUM }
                val risk       = scoreRisk(pkg.packageName, dangerous, medium)

                result.add(JSONObject().apply {
                    put("id",            pkg.packageName.hashCode())
                    put("name",          pm.getApplicationLabel(appInfo).toString())
                    put("package",       pkg.packageName)
                    put("cat",           guessCategory(appInfo))
                    put("version",       pkg.versionName ?: "—")
                    put("installed",     fmt.format(Date(pkg.firstInstallTime)))
                    put("risk",          risk.getString("level"))
                    put("score",         risk.getInt("score"))
                    put("totalPerms",    allPerms.size)
                    put("dangerousCount",dangerous.size)
                    put("perms",         buildPermArray(dangerous, medium, allPerms))
                    put("knownFlag",     HIGH_RISK[pkg.packageName] ?: "")
                })
            }

            // Mayor riesgo primero
            result.sortByDescending { it.getInt("score") }

            JSONArray().also { arr -> result.forEach { arr.put(it) } }.toString()

        } catch (e: Exception) {
            JSONArray().put(
                JSONObject().put("error", e.message ?: "Error desconocido")
            ).toString()
        }
    }

    /**
     * Devuelve el ícono real de una app como data:image/png;base64,...
     * Si falla (app desinstalada, error), devuelve "".
     *
     * JS:
     *   const src = Android.getAppIcon("com.example.app")
     *   if (src) imgElement.src = src
     */
    @JavascriptInterface
    fun getAppIcon(packageName: String): String {
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val size     = 96
            val bitmap   = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas   = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
            "data:image/png;base64," +
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Lógica interna
    // ═════════════════════════════════════════════════════════════════════════

    private fun scoreRisk(
        pkg: String,
        dangerous: List<String>,
        medium: List<String>
    ): JSONObject {
        if (pkg in TRUSTED)   return risk("seguro", 5)
        if (pkg in HIGH_RISK) return risk("alto",   90)

        var score = 0

        // Combo crítico: cámara + micrófono + ubicación + contactos juntos
        val combo = listOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_CONTACTS"
        ).count { it in dangerous }

        score += combo * 18
        score += (dangerous.size - combo).coerceAtLeast(0) * 8
        score += medium.size * 4

        if ("android.permission.READ_SMS"                   in dangerous) score += 15
        if ("android.permission.ACCESS_BACKGROUND_LOCATION" in dangerous) score += 12
        if ("android.permission.READ_CALL_LOG"              in dangerous) score += 10
        if ("android.permission.PROCESS_OUTGOING_CALLS"     in dangerous) score += 8

        score = score.coerceIn(0, 100)

        return risk(
            level = when {
                score >= 65 -> "alto"
                score >= 35 -> "medio"
                score >= 15 -> "bajo"
                else        -> "seguro"
            },
            score = score
        )
    }

    private fun risk(level: String, score: Int): JSONObject =
        JSONObject().apply { put("level", level); put("score", score) }

    private fun buildPermArray(
        dangerous: List<String>,
        medium: List<String>,
        all: List<String>
    ): JSONArray {
        val arr = JSONArray()
        (dangerous + medium).take(12).forEach { p ->
            arr.put(JSONObject().apply {
                put("name",  LABEL[p] ?: p.substringAfterLast("."))
                put("level", if (p in DANGEROUS) "r" else "o")
            })
        }
        if ("android.permission.INTERNET" in all)
            arr.put(JSONObject().apply { put("name", "Internet"); put("level", "g") })
        return arr
    }

    private fun guessCategory(info: ApplicationInfo): String =
        when (info.category) {
            ApplicationInfo.CATEGORY_SOCIAL       -> "redes sociales"
            ApplicationInfo.CATEGORY_GAME         -> "juegos"
            ApplicationInfo.CATEGORY_AUDIO        -> "música y audio"
            ApplicationInfo.CATEGORY_VIDEO        -> "video"
            ApplicationInfo.CATEGORY_IMAGE        -> "fotografía"
            ApplicationInfo.CATEGORY_NEWS         -> "noticias"
            ApplicationInfo.CATEGORY_MAPS         -> "mapas y navegación"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productividad"
            else -> {
                val p = info.packageName.lowercase()
                when {
                    "bank"    in p || "pay"    in p || "wallet"  in p -> "finanzas"
                    "shop"    in p || "market" in p || "store"   in p -> "compras"
                    "health"  in p || "fitness" in p || "sport"  in p -> "salud"
                    "camera"  in p || "photo"  in p || "gallery" in p -> "fotografía"
                    "music"   in p || "spotify" in p                  -> "música"
                    "video"   in p || "player" in p || "stream"  in p -> "video"
                    "browser" in p || "chrome" in p || "firefox" in p -> "navegador"
                    "mail"    in p || "email"  in p                   -> "correo"
                    "map"     in p || "gps"    in p || "nav"     in p -> "navegación"
                    "chat"    in p || "message" in p || "call"   in p -> "mensajería"
                    "vpn"     in p || "secure" in p || "password" in p -> "seguridad"
                    else                                               -> "utilidades"
                }
            }
        }
}
