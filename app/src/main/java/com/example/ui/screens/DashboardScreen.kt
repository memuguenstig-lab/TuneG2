package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Fill
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
            ConnectionStatusBadge(connState)

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
        val calculatedMaxSpeed = if (telemetry.speedLimitUnlocked) {
            if (telemetry.customMaxSpeed > 0f) telemetry.customMaxSpeed else 120f
        } else {
            25f
        }
        SpeedometerSection(
            speed = telemetry.speedKmh,
            maxSpeed = calculatedMaxSpeed,
            turboActive = telemetry.turboModeEnabled,
            reverseActive = telemetry.reverseGearEnabled,
            soundActive = telemetry.soundSimulationEnabled
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Visual handlebar/steering angle section
        HandlebarSection(angle = 0f)

        Spacer(modifier = Modifier.height(16.dp))

        if (connState == ConnectionState.CONNECTED) {
            DriveModePresetsCard(
                currentSpeedLimit = telemetry.customMaxSpeed,
                isTurbo = telemetry.turboModeEnabled,
                isDual = telemetry.dualMotorEnabled,
                onPresetSelected = { viewModel.applyDriveModePreset(it) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Basic Info grid (Volt, Amps, Temp, Mileage)
        TelemetryGridSection(telemetry)

        Spacer(modifier = Modifier.height(16.dp))

        if (connState == ConnectionState.CONNECTED) {
            BatteryDiagnosticsCard(telemetry = telemetry)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Interactive control cards (Gears, Dual Motor, Lock, Light)
        if (connState == ConnectionState.CONNECTED) {
            TuningCard(telemetry, viewModel)
            Spacer(modifier = Modifier.height(16.dp))
            AssistCard(telemetry, viewModel)
            Spacer(modifier = Modifier.height(16.dp))
            SecurityCard(telemetry, viewModel)
            Spacer(modifier = Modifier.height(20.dp))
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
                        text = "Suche im Bluetooth-Tab nach verfügbaren KuKirin G2 E-Scootern.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

        if (telemetry.lockStatus && connState == ConnectionState.CONNECTED) {
            PinLockScreen(
                onUnlockSuccess = { viewModel.toggleLock(false) }
            )
        }
    }
}

@Composable
fun ConnectionStatusBadge(connState: ConnectionState) {
    val (text, color) = when (connState) {
        ConnectionState.DISCONNECTED -> "GETRENNT" to MaterialTheme.colorScheme.error
        ConnectionState.CONNECTING -> "VERBINDET..." to MaterialTheme.colorScheme.secondary
        ConnectionState.CONNECTED -> "VERBUNDEN" to MaterialTheme.colorScheme.primary
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
fun SpeedometerSection(
    speed: Float,
    maxSpeed: Float,
    turboActive: Boolean = false,
    reverseActive: Boolean = false,
    soundActive: Boolean = false
) {
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
            
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (reverseActive) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "RÜCKWÄRTS (R)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (turboActive) {
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
                if (soundActive) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "SOUND SYNTH",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HandlebarSection(angle: Float) {
    val animatedAngle by animateFloatAsState(
        targetValue = angle,
        animationSpec = tween(durationMillis = 100),
        label = "HandlebarAngle"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp, 60.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                
                rotate(degrees = animatedAngle, pivot = center) {
                    // Draw central stem extension
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        start = center,
                        end = Offset(center.x, center.y - 12.dp.toPx()),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    
                    // Draw main handlebar line (left to right)
                    val barWidth = 100.dp.toPx()
                    drawLine(
                        color = Color.White,
                        start = Offset(center.x - barWidth / 2, center.y - 12.dp.toPx()),
                        end = Offset(center.x + barWidth / 2, center.y - 12.dp.toPx()),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    
                    // Draw left grip (accent color, e.g. DarkGray)
                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(center.x - barWidth / 2, center.y - 12.dp.toPx()),
                        end = Offset(center.x - barWidth / 2 + 18.dp.toPx(), center.y - 12.dp.toPx()),
                        strokeWidth = 10.dp.toPx(),
                        cap = StrokeCap.Square
                    )
                    
                    // Draw right grip (accent color, e.g. DarkGray)
                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(center.x + barWidth / 2 - 18.dp.toPx(), center.y - 12.dp.toPx()),
                        end = Offset(center.x + barWidth / 2, center.y - 12.dp.toPx()),
                        strokeWidth = 10.dp.toPx(),
                        cap = StrokeCap.Square
                    )
                    
                    // Draw central display box on handlebar
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(center.x - 12.dp.toPx(), center.y - 20.dp.toPx()),
                        size = Size(24.dp.toPx(), 16.dp.toPx()),
                        style = Fill
                    )
                    drawRect(
                        color = Color.Green.copy(alpha = 0.8f),
                        topLeft = Offset(center.x - 12.dp.toPx(), center.y - 20.dp.toPx()),
                        size = Size(24.dp.toPx(), 16.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                
                // Stationary central base point
                drawCircle(
                    color = Color.Gray,
                    radius = 5.dp.toPx(),
                    center = center
                )
            }
        }
        
        // Text readout of direction
        val directionText = when {
            angle < -2f -> "LINKS LENKEN: ${String.format("%.0f", -angle)}°"
            angle > 2f -> "RECHTS LENKEN: ${String.format("%.0f", angle)}°"
            else -> "GERADEAUS"
        }
        val directionColor = when {
            angle < -2f -> MaterialTheme.colorScheme.primary
            angle > 2f -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        }
        
        Text(
            text = directionText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = directionColor
        )
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
fun TuningCard(
    telemetry: ScooterTelemetry,
    viewModel: ScooterViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "TUNING & PERFORMANCE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(14.dp))

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
                            text = "SPEED LIMIT UNLOCKED: Scooter ist getunt (vMax ~120 km/h)!",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

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
                listOf(1 to "ECO", 2 to "SPORT", 3 to "TURBO").forEach { (g, label) ->
                    val isSelected = telemetry.currentGear == g
                    val btnBg = if (isSelected) {
                        if (telemetry.speedLimitUnlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val btnFg = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant

                    Button(
                        onClick = { viewModel.setGear(g) },
                        modifier = Modifier.weight(1f).height(44.dp).testTag("gear_button_$g"),
                        colors = ButtonDefaults.buttonColors(containerColor = btnBg, contentColor = btnFg),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(text = "G$g", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Switch: Dual Motor
            SwitchRow(
                icon = Icons.Default.FlashOn,
                title = "Allradmotor (Dual Motor)",
                desc = "Aktiviert beide Motoren für maximales Drehmoment",
                checked = telemetry.dualMotorEnabled,
                onCheckedChange = { viewModel.toggleDualMotor(it) },
                iconTint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Switch: Speed Unlock
            SwitchRow(
                icon = Icons.Default.Speed,
                title = "Geschwindigkeitssperre aufheben",
                desc = "Entsperrt die vMax-Grenze von 20/25 km/h",
                checked = telemetry.speedLimitUnlocked,
                onCheckedChange = { viewModel.toggleSpeedLimit(it) },
                iconTint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Switch: Turbo Mode
            SwitchRow(
                icon = Icons.Default.FlashOn,
                title = "Werksseitiger Turbo-Modus",
                desc = "Schaltet das versteckte Turbo-Leistungsregister frei",
                checked = telemetry.turboModeEnabled,
                onCheckedChange = { viewModel.toggleTurboMode(it) },
                iconTint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AssistCard(
    telemetry: ScooterTelemetry,
    viewModel: ScooterViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "FAHRASSISTENZ & KOMFORT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(14.dp))

            SwitchRow(
                icon = Icons.Default.Speed,
                title = "Tempomat (Cruise Control)",
                desc = "Hält die aktuelle Geschwindigkeit automatisch",
                checked = telemetry.cruiseControlEnabled,
                onCheckedChange = { viewModel.toggleCruiseControl(it) },
                iconTint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            SwitchRow(
                icon = Icons.Default.PlayArrow,
                title = "Direktstart (Zero Start)",
                desc = "Fahren ohne vorheriges Anschieben (Kickstart aus)",
                checked = telemetry.zeroStartEnabled,
                onCheckedChange = { viewModel.toggleZeroStart(it) },
                iconTint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            SwitchRow(
                icon = Icons.Default.FlashOn,
                title = "Rekuperation (Motorbremse)",
                desc = "Gewinnt beim Bremsen & Rollen Energie zurück",
                checked = telemetry.kersEnabled,
                onCheckedChange = { viewModel.toggleKers(it) },
                iconTint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            SwitchRow(
                icon = Icons.Default.DirectionsRun,
                title = "Sanfter Anlauf (Soft-Start)",
                desc = "Reduziert Drehmoment beim Anfahren bei Nässe",
                checked = telemetry.softStartEnabled,
                onCheckedChange = { viewModel.toggleSoftStart(it) },
                iconTint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            SwitchRow(
                icon = Icons.Default.Refresh,
                title = "Rückwärtsgang",
                desc = "Aktiviert den Elektromotor-Rückwärtslauf (vMax 5 km/h)",
                checked = telemetry.reverseGearEnabled,
                onCheckedChange = { viewModel.toggleReverseGear(it) },
                iconTint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun SecurityCard(
    telemetry: ScooterTelemetry,
    viewModel: ScooterViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ERWEITERTE FUNKTIONEN & SICHERHEIT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(14.dp))

            SwitchRow(
                icon = Icons.Default.Lock,
                title = "Diebstahl-Warnanlage",
                desc = "Löst akustischen Alarm bei unbefugter Bewegung aus",
                checked = telemetry.antiTheftEnabled,
                onCheckedChange = { viewModel.toggleAntiTheft(it) },
                iconTint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            SwitchRow(
                icon = Icons.Default.Warning,
                title = "Übertemperaturschutz umgehen",
                desc = "Verhindert Abschaltung bei starker Hitzebelastung",
                checked = telemetry.tempProtectionBypassed,
                onCheckedChange = { viewModel.toggleTempProtectionBypass(it) },
                iconTint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))

            SwitchRow(
                icon = Icons.Default.Lightbulb,
                title = "Motorsound-Simulator",
                desc = "Simuliert futuristisches Triebwerk proportional zur Drehzahl",
                checked = telemetry.soundSimulationEnabled,
                onCheckedChange = { viewModel.toggleSoundSimulation(it) },
                iconTint = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mini utility buttons (Light, Lock)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { viewModel.toggleLight(!telemetry.lightStatus) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("light_button"),
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
                    onClick = { viewModel.toggleLock(!telemetry.lockStatus) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("lock_button"),
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
                        text = if (telemetry.lockStatus) "Gesperrt" else "Frei",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (telemetry.lockStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (checked) iconTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = iconTint)
        )
    }
}

@Composable
fun SimulatorControlCard(
    throttleValue: Float,
    onThrottleChange: (Float) -> Unit,
    onBrakeChange: (Boolean) -> Unit,
    steeringAngle: Float,
    onSteeringChange: (Float) -> Unit
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

            Spacer(modifier = Modifier.height(8.dp))

            // Lenkung slider (Steering Angle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lenkung:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(65.dp)
                )
                Slider(
                    value = steeringAngle,
                    onValueChange = onSteeringChange,
                    valueRange = -45f..45f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulator_steering_slider")
                )
                
                // Snap to center icon button
                IconButton(
                    onClick = { onSteeringChange(0f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Geradeaus",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
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

@Composable
fun DriveModePresetsCard(
    currentSpeedLimit: Float,
    isTurbo: Boolean,
    isDual: Boolean,
    onPresetSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "FAHRPROFIL-SCHNELLWAHL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Row of Profiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf(
                    Triple("ECO", "20 km/h", Color(0xFF4CAF50)),
                    Triple("STVZO", "22 km/h", Color(0xFF2196F3)),
                    Triple("SPORT", "38 km/h", Color(0xFFFF9800)),
                    Triple("RACE", "120 km/h", Color(0xFFE91E63))
                )

                modes.forEach { (name, label, color) ->
                    val isActive = when (name) {
                        "ECO" -> currentSpeedLimit == 20f && !isTurbo && !isDual
                        "STVZO" -> currentSpeedLimit == 22f && !isTurbo && !isDual
                        "SPORT" -> currentSpeedLimit == 38f && !isTurbo && isDual
                        "RACE" -> currentSpeedLimit == 120f && isTurbo && isDual
                        else -> false
                    }

                    Button(
                        onClick = { onPresetSelected(if (name == "STVZO") "DE_STVZO" else name) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("preset_${name.lowercase()}_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            contentColor = if (isActive) color else MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = if (isActive) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = name,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) color.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryDiagnosticsCard(telemetry: ScooterTelemetry) {
    var expanded by remember { mutableStateOf(false) }

    // Range estimation based on battery percent and speed profile
    val rangeFactor = when {
        telemetry.customMaxSpeed <= 20f -> 0.90f // ECO: up to 90km
        telemetry.customMaxSpeed <= 22f -> 0.82f // STVZO: up to 82km
        telemetry.customMaxSpeed <= 38f -> 0.58f // SPORT: up to 58km
        else -> 0.36f // RACE: up to 36km
    }

    val estimatedRange = telemetry.batteryPercent * rangeFactor
    val cellCount = 13
    val avgCellVolt = telemetry.voltage / cellCount

    // Generate simulated balance values that fluctuate slightly with time
    val timeMillis = System.currentTimeMillis()
    val cellVoltages = remember(telemetry.voltage, timeMillis / 4000) {
        List(cellCount) { index ->
            val offset = when (index) {
                0 -> 0.008f
                1 -> -0.012f
                2 -> 0.005f
                3 -> 0.014f
                4 -> -0.006f
                5 -> 0.002f
                6 -> -0.015f
                7 -> 0.009f
                8 -> -0.003f
                9 -> 0.011f
                10 -> -0.007f
                11 -> 0.004f
                12 -> -0.002f
                else -> 0f
            }
            val dynamicFluct = (Math.sin(timeMillis.toDouble() / 1500.0 + index) * 0.0015).toFloat()
            (avgCellVolt + offset + dynamicFluct).coerceIn(3.0f, 4.25f)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Battery Diagnostics",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BATTERIE-DIAGNOSE & BMS",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Toggle Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Range and dynamic state Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "RESTREICHWEITE EST.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = String.format("%.1f km", estimatedRange),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ZELLEN-DRIFT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    val delta = cellVoltages.maxOrNull()!! - cellVoltages.minOrNull()!!
                    Text(
                        text = String.format("%.0f mV Δ", delta * 1000),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (delta < 0.015f) Color(0xFF4CAF50) else if (delta < 0.035f) Color(0xFFFF9800) else Color(0xFFF44336)
                    )
                }
            }

            // Expanded Cell View
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "13S LI-ION INDIVIDUELLES MONITORING (48V PACK)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val chunks = cellVoltages.chunked(4)
                    chunks.forEachIndexed { rowIndex, chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            chunk.forEachIndexed { colIndex, voltage ->
                                val cellNum = rowIndex * 4 + colIndex + 1
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = String.format("Z%02d", cellNum),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = String.format("%.3fV", voltage),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (voltage > 3.7f) MaterialTheme.colorScheme.onSurface else Color(0xFFFF9800)
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        val pct = ((voltage - 3.0f) / 1.2f).coerceIn(0f, 1f)
                                        LinearProgressIndicator(
                                            progress = { pct },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp),
                                            color = if (pct > 0.5f) Color(0xFF4CAF50) else if (pct > 0.2f) Color(0xFFFF9800) else Color(0xFFF44336),
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                        )
                                    }
                                }
                            }

                            if (chunk.size < 4) {
                                repeat(4 - chunk.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinLockScreen(
    onUnlockSuccess: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val correctPin = "1234"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Wegfahrsperre Aktiv",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp)
            )

            Text(
                text = "WEGFAHRSPERRE AKTIV",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bitte gib den 4-stelligen PIN-Code ein, um den KuKirin G2 Controller freizuschalten.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val isFilled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .background(
                                color = if (isFilled) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            val buttons = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "⌫")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                buttons.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        row.forEach { char ->
                            Button(
                                onClick = {
                                    when (char) {
                                        "C" -> {
                                            enteredPin = ""
                                            errorMessage = ""
                                        }
                                        "⌫" -> {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.dropLast(1)
                                            }
                                            errorMessage = ""
                                        }
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                enteredPin += char
                                            }

                                            if (enteredPin.length == 4) {
                                                if (enteredPin == correctPin) {
                                                    onUnlockSuccess()
                                                } else {
                                                    errorMessage = "Falscher PIN-Code! Zugriff verweigert."
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(68.dp)
                                    .testTag("pin_pad_${if (char == "⌫") "backspace" else if (char == "C") "clear" else char}"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (char == "C" || char == "⌫") Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White
                                ),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = char,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
