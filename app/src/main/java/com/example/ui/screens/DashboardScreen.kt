package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScooterTelemetry
import com.example.service.ConnectionState
import com.example.ui.ScooterViewModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DashboardScreen(
    viewModel: ScooterViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsState()
    val connState by viewModel.connectionState.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val rideSec by viewModel.rideSeconds.collectAsState()
    val rideDist by viewModel.rideDistance.collectAsState()
    val isSimMode by viewModel.isSimulationMode.collectAsState()

    var simulateThrottleValue by remember { mutableFloatStateOf(0f) }

    // Sync physical slider / throttle value with manager
    LaunchedEffect(simulateThrottleValue) {
        if (isSimMode) {
            viewModel.simulateThrottle(simulateThrottleValue)
        }
    }

    // Reset throttle when disconnected or simulation mode changes
    LaunchedEffect(connState, isSimMode) {
        if (connState != ConnectionState.CONNECTED) {
            simulateThrottleValue = 0f
            viewModel.simulateThrottle(0f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Connection & Record Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Tag
            ConnectionStatusBadge(connState, isSimMode)

            // Active Trip Recording Controls
            if (connState == ConnectionState.CONNECTED) {
                ActiveRecordingBadge(
                    isRecording = isRecording,
                    rideSec = rideSec,
                    rideDist = rideDist,
                    onToggleRecording = { viewModel.toggleRideRecording() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display Active Error Codes if present
        if (telemetry.errorCodes.isNotEmpty()) {
            ErrorAlertCard(telemetry.errorCodes)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Circular Speedometer Arc View
        val calculatedMaxSpeed = if (telemetry.speedLimitUnlocked) telemetry.customMaxSpeed else 25f
        SpeedometerSection(
            speed = telemetry.speedKmh,
            maxSpeed = calculatedMaxSpeed,
            turboActive = telemetry.turboModeEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Basic Info grid (Volt, Amps, Temp, Mileage)
        TelemetryGridSection(telemetry)

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive control cards (Gears, Dual Motor, Lock, Light)
        if (connState == ConnectionState.CONNECTED) {
            ControlCenterCard(
                telemetry = telemetry,
                onGearChange = { viewModel.setGear(it) },
                onToggleDual = { viewModel.toggleDualMotor(it) },
                onToggleUnlock = { viewModel.toggleSpeedLimit(it) },
                onToggleLight = { viewModel.toggleLight(it) },
                onToggleLock = { viewModel.toggleLock(it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Interactive emulator throttle (only active in simulation or if connected)
            if (isSimMode) {
                SimulatorControlCard(
                    throttleValue = simulateThrottleValue,
                    onThrottleChange = { simulateThrottleValue = it },
                    onBrakeChange = { pressed ->
                        viewModel.simulateBrake(pressed)
                        if (pressed) {
                            simulateThrottleValue = 0f
                        }
                    }
                )
            }
        } else {
            // Friendly disconnected state notice
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Not Connected",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nicht verbunden",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Suche im Bluetooth-Tab nach verfügbaren KuKirin G2 E-Scootern oder aktiviere den Simulationsmodus.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBadge(connState: ConnectionState, isSimMode: Boolean) {
    val (text, color) = when (connState) {
        ConnectionState.DISCONNECTED -> "GETRENNT" to MaterialTheme.colorScheme.error
        ConnectionState.CONNECTING -> "VERBINDET..." to MaterialTheme.colorScheme.secondary
        ConnectionState.CONNECTED -> {
            if (isSimMode) "CONNECTED (SIM)" to MaterialTheme.colorScheme.primary
            else "CONNECTED (BLE)" to MaterialTheme.colorScheme.primary
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.testTag("connection_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun ActiveRecordingBadge(
    isRecording: Boolean,
    rideSec: Long,
    rideDist: Double,
    onToggleRecording: () -> Unit
) {
    val durationText = String.format("%02d:%02d", rideSec / 60, rideSec % 60)
    val distanceText = String.format("%.2f km", rideDist)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isRecording) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "RECORDING",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$distanceText ($durationText)",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Button(
            onClick = onToggleRecording,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            ),
            contentPadding = RowDefaults_ButtonPadding(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .height(36.dp)
                .testTag("record_ride_button")
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isRecording) "Stop" else "Rec",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isRecording) "Stop" else "Tour Aufzeichnen",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun RowDefaults_ButtonPadding() = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)

@Composable
fun ErrorAlertCard(errors: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error Warning",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Systemfehler erkannt!",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                errors.forEach { error ->
                    Text(
                        text = "• $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedometerSection(speed: Float, maxSpeed: Float, turboActive: Boolean = false) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(durationMillis = 150),
        label = "SpeedNeedle"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .aspectRatio(1.2f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height * 0.85f)
            val radius = size.width * 0.44f

            // Draw speedometer grey background arc (180 degrees)
            drawArc(
                color = Color.Gray.copy(alpha = 0.2f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw glowing speed sweep arc
            val sweepAngle = min(180f, (animatedSpeed / maxSpeed) * 180f)
            drawArc(
                brush = Brush.horizontalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.5f), primaryColor)
                ),
                startAngle = 180f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw dial ticks
            val totalTicks = 11
            for (i in 0 until totalTicks) {
                val angleRad = (180f + (i * 18f)) * PI / 180f
                val innerRadius = radius - 10.dp.toPx()
                val outerRadius = radius + 4.dp.toPx()

                val startX = center.x + innerRadius * cos(angleRad).toFloat()
                val startY = center.y + innerRadius * sin(angleRad).toFloat()
                val endX = center.x + outerRadius * cos(angleRad).toFloat()
                val endY = center.y + outerRadius * sin(angleRad).toFloat()

                drawLine(
                    color = Color.Gray.copy(alpha = 0.4f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // Center typography speedometer readout
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%.1f", speed).replace(",", "."),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            
            if (turboActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TURBO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryGridSection(telemetry: ScooterTelemetry) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TelemetryMetricCard(
                label = "AKKUSTAND",
                value = "${telemetry.batteryPercent}%",
                detail = "${telemetry.voltage} V",
                icon = Icons.Default.FlashOn,
                color = if (telemetry.batteryPercent > 20) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            TelemetryMetricCard(
                label = "LEISTUNG",
                value = "${telemetry.powerWatts} W",
                detail = "${telemetry.currentAmps} A",
                icon = Icons.Default.FlashOn,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            TelemetryMetricCard(
                label = "TEMPERATUR",
                value = "${telemetry.tempC} °C",
                detail = if (telemetry.tempC > 65) "Heiß" else "Optimal",
                icon = Icons.Default.Warning,
                color = if (telemetry.tempC > 65) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            TelemetryMetricCard(
                label = "GEFAHREN KM",
                value = "${telemetry.tripMileageKm} km",
                detail = "Gesamt: ${telemetry.totalMileageKm} km",
                icon = Icons.Default.Speed,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TelemetryMetricCard(
    label: String,
    value: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ControlCenterCard(
    telemetry: ScooterTelemetry,
    onGearChange: (Int) -> Unit,
    onToggleDual: (Boolean) -> Unit,
    onToggleUnlock: (Boolean) -> Unit,
    onToggleLight: (Boolean) -> Unit,
    onToggleLock: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "TUNING & STEUERUNG",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Speed limit warning for Tuning
            if (telemetry.speedLimitUnlocked) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SPEED LIMIT UNLOCKED: Scooter ist getunt (vMax ~65 km/h)!",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Gear Selector Row
            Text(
                text = "FAHRMODUS (GÄNGE)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    1 to "ECO (vMax 15/25)",
                    2 to "SPORT (vMax 20/45)",
                    3 to "TURBO (vMax 25/65)"
                ).forEach { (g, label) ->
                    val isSelected = telemetry.currentGear == g
                    val btnBg = if (isSelected) {
                        if (telemetry.speedLimitUnlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val btnFg = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant

                    Button(
                        onClick = { onGearChange(g) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("gear_button_$g"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = btnBg,
                            contentColor = btnFg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "G$g",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control switches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Dual Motor",
                        tint = if (telemetry.dualMotorEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Allradmotor (Dual Motor)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Schaltet beide Motoren zu für max. Drehmoment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked = telemetry.dualMotorEnabled,
                    onCheckedChange = onToggleDual,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.testTag("dual_motor_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Speed Unlock",
                        tint = if (telemetry.speedLimitUnlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Sperre aufheben (Tuning)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Entsperrt die vMax-Grenze von 20/25 km/h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked = telemetry.speedLimitUnlocked,
                    onCheckedChange = onToggleUnlock,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.testTag("tuning_speed_switch")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mini utility buttons (Light, Lock)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { onToggleLight(!telemetry.lightStatus) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("light_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (telemetry.lightStatus) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Light",
                        tint = if (telemetry.lightStatus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (telemetry.lightStatus) "Licht: An" else "Licht: Aus",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (telemetry.lightStatus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { onToggleLock(!telemetry.lockStatus) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("lock_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (telemetry.lockStatus) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (telemetry.lockStatus) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock",
                        tint = if (telemetry.lockStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (telemetry.lockStatus) "Scooter: Gesperrt" else "Scooter: Frei",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (telemetry.lockStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SimulatorControlCard(
    throttleValue: Float,
    onThrottleChange: (Float) -> Unit,
    onBrakeChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SIMULATOR STEUERUNG (EMULATOR)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Gasgriff slider (Throttle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gasgriff:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(65.dp)
                )
                Slider(
                    value = throttleValue,
                    onValueChange = onThrottleChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulator_throttle_slider")
                )
                Text(
                    text = "${(throttleValue * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Brake press button (Touch gestures)
            var isBraking by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isBraking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(
                            alpha = 0.25f
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isBraking = true
                                onBrakeChange(true)
                                tryAwaitRelease()
                                isBraking = false
                                onBrakeChange(false)
                            }
                        )
                    }
                    .testTag("simulator_brake_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isBraking) "BREMSE AKTIV" else "HALTEN ZUM BREMSEN",
                    color = if (isBraking) Color.White else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
