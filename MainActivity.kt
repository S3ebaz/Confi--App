package com.confiapp

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.confiapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pantalla completa: el contenido va debajo de las barras del sistema
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Íconos blancos en status bar y nav bar (tema oscuro)
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars     = false
            isAppearanceLightNavigationBars = false
        }

        webView = binding.webView
        setupWebView()
        webView.loadUrl("file:///android_asset/www/index.html")

        // Botón físico Atrás: navega dentro del WebView o cierra la app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled       = true   // necesario para toda la lógica de la app
            domStorageEnabled       = true   // permite localStorage en JS
            allowFileAccess         = true   // acceso a file:///android_asset/
            allowContentAccess      = true
            setSupportZoom(false)
            builtInZoomControls     = false
            displayZoomControls     = false
            useWideViewPort         = true
            loadWithOverviewMode    = true
            defaultTextEncodingName = "UTF-8"
        }

        // Registrar el puente: en JS se llama como Android.metodo()
        webView.addJavascriptInterface(AppBridge(this), "Android")

        // Interceptar navegación: solo permitir assets locales y la API de Claude
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return !url.startsWith("file://") &&
                       !url.startsWith("https://api.anthropic.com")
            }
        }
    }
}
