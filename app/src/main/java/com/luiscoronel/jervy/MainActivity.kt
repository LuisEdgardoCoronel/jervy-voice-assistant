package com.luiscoronel.jervy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.luiscoronel.jervy.system.JervyService
import com.luiscoronel.jervy.ui.MainScreen
import com.luiscoronel.jervy.ui.theme.JervyTheme

class MainActivity : ComponentActivity() {
    /** Mínimo para arrancar el servicio. El onboarding completo de permisos es R10 (etapa 3). */
    private val requiredPermissions: Array<String> =
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as JervyApplication).container
        setContent {
            JervyTheme {
                val status by container.serviceStatus.collectAsState()
                var permissionsMissing by remember { mutableStateOf(false) }
                val permissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) { results ->
                        permissionsMissing = !results.values.all { it }
                        if (!permissionsMissing) startJervyService()
                    }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        status = status,
                        permissionsMissing = permissionsMissing,
                        onActivate = {
                            if (hasRequiredPermissions()) {
                                permissionsMissing = false
                                startJervyService()
                            } else {
                                permissionLauncher.launch(requiredPermissions)
                            }
                        },
                        onPause = { sendServiceAction(JervyService.ACTION_PAUSE) },
                        onResume = { sendServiceAction(JervyService.ACTION_RESUME) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun startJervyService() {
        ContextCompat.startForegroundService(this, serviceIntent(JervyService.ACTION_START))
    }

    private fun sendServiceAction(action: String) {
        startService(serviceIntent(action))
    }

    private fun serviceIntent(action: String) = Intent(this, JervyService::class.java).setAction(action)
}
