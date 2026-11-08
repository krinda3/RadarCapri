package com.pridalac.radarcapri.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pridalac.radarcapri.MainViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val alertDistance by viewModel.prefs.alertDistance.collectAsStateWithLifecycle(initialValue = 300f)
    val minSpeed by viewModel.prefs.minSpeed.collectAsStateWithLifecycle(initialValue = 5f)
    val ttsEnabled by viewModel.prefs.ttsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val autoStart by viewModel.prefs.autoStart.collectAsStateWithLifecycle(initialValue = true)
    val beepEnabled by viewModel.prefs.beepEnabled.collectAsStateWithLifecycle(initialValue = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // --- Alert distance ---
            Text(
                text = "Distancia de aviso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${alertDistance.roundToInt()} metros",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = alertDistance,
                onValueChange = { scope.launch { viewModel.prefs.setAlertDistance(it) } },
                valueRange = 100f..1000f,
                steps = 17, // 50 m steps
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("100 m", style = MaterialTheme.typography.labelSmall)
                Text("1000 m", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Minimum speed ---
            Text(
                text = "Velocidad mínima para avisar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${minSpeed.roundToInt()} km/h",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "No avisa si vas más despacio (ej. parking)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = minSpeed,
                onValueChange = { scope.launch { viewModel.prefs.setMinSpeed(it) } },
                valueRange = 0f..30f,
                steps = 29,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0 km/h", style = MaterialTheme.typography.labelSmall)
                Text("30 km/h", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- TTS toggle ---
            SettingsToggleRow(
                title = "Avisos de voz (TTS)",
                subtitle = "Anuncia tipo, distancia y límite al acercarte",
                checked = ttsEnabled,
                onCheckedChange = { scope.launch { viewModel.prefs.setTtsEnabled(it) } }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- Beep toggle ---
            SettingsToggleRow(
                title = "Pitido por exceso de velocidad",
                subtitle = "Suena cada 5 s si superas el límite del radar",
                checked = beepEnabled,
                onCheckedChange = { scope.launch { viewModel.prefs.setBeepEnabled(it) } }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- Auto start toggle ---
            SettingsToggleRow(
                title = "Iniciar al abrir la app",
                subtitle = "El servicio de avisos arranca solo al abrir RadarCapri",
                checked = autoStart,
                onCheckedChange = { scope.launch { viewModel.prefs.setAutoStart(it) } }
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
