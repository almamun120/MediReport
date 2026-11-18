package com.medireport.portal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.medireport.portal.data.ThemeMode
import com.medireport.portal.ui.AppRoot
import com.medireport.portal.ui.theme.MediReportTheme
import com.medireport.portal.sms.SmsProcessor
import com.medireport.portal.web.PortalService
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {
    private var darkTheme = false
    private var heroScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as App).repository
        applyBars()

        lifecycleScope.launch {
            repo.seed()
            if (repo.prefs.getPortal().autoStart) PortalService.start(this@MainActivity)
            if (repo.prefs.getSms().enabled) SmsProcessor.kick(this@MainActivity)
        }

        setContent {
            val mode by repo.prefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                darkTheme = dark
                applyBars()
            }

            MediReportTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(onHeroChanged = { hero ->
                        heroScreen = hero
                        applyBars()
                    })
                }
            }
        }
    }

    /** Light status-bar icons on dark surfaces (dark theme or gradient hero screens), dark icons otherwise. */
    private fun applyBars() {
        val lightIcons = darkTheme || heroScreen
        val status = if (lightIcons) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        val nav = if (darkTheme) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = status, navigationBarStyle = nav)
    }
}
