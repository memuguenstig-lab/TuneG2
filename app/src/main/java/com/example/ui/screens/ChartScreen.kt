package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MultilineChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ConnectionState
import com.example.ui.ScooterViewModel
import com.example.ui.TelemetryDataPoint
import kotlin.math.max

@Composable
fun ChartScreen(
    viewModel: ScooterViewModel,
    modifier: Modifier = Modifier
) {
    val chartPoints by viewModel.chartPoints.collectAsState()
    val connState by viewModel.connectionState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TELEMETRIE ECHTZEIT-DIAGRAMM",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Echtzeit-Verlauf von Geschwindigkeit (km/h) und Stromstärke (Ampere)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (connState != ConnectionState.CONNECTED) {
            // Disconnected Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MultilineChart,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Keine Echtzeitdaten",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Verbinde dich zuerst mit einem KuKirin G2 Scooter, um Diagramme in Echtzeit aufzuzeichnen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else if (chartPoints.size < 2) {
            // Collecting state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Verbindungsaufbau & Datensammlung...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            // Drawing the beautiful Custom Canvas Line Chart!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("telemetry_chart_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Legend
                    ChartLegendRow()

                    Spacer(modifier = Modifier.height(12.dp))

                    // Graphic Drawing Board
                    Box(modifier = Modifier.weight(1f)) {
                        TelemetryLineChartCanvas(points = chartPoints)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Footnote Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Zeigt die letzten ${chartPoints.size} Sekunden kontinuierlicher Fahrt.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChartLegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp, 4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Geschwindigkeit (km/h)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp, 4.dp)
                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Stromverbrauch (Ampere)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TelemetryLineChartCanvas(points: List<TelemetryDataPoint>) {
    val speedColor = MaterialTheme.colorScheme.primary
    val currentColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Draw Grid Lines and Labels
        val verticalGridLines = 4
        val horizontalGridLines = 5

        // Draw horizontal grid lines (for scale reference)
        for (i in 0..horizontalGridLines) {
            val y = (height / horizontalGridLines) * i
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw vertical grid lines (representing time ticks)
        for (i in 0..verticalGridLines) {
            val x = (width / verticalGridLines) * i
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Scale bounds
        // Speed: 0 to 70 km/h (fixed for visual layout continuity)
        val maxSpeedVal = 70f
        // Current: -5A to 40A (fixed)
        val minAmpsVal = -5f
        val maxAmpsVal = 40f
        val ampsRange = maxAmpsVal - minAmpsVal

        // 2. Draw Speed Path (Primary line)
        val speedPath = Path()
        val speedAreaPath = Path() // For gradient fill

        points.forEachIndexed { index, point ->
            val x = (width / (points.size - 1)) * index
            // Map speed to vertical inverted coordinates (0 speed is bottom height, 70 is top 0)
            val ySpeed = height - ((point.speed / maxSpeedVal) * height)

            if (index == 0) {
                speedPath.moveTo(x, ySpeed)
                speedAreaPath.moveTo(x, height)
                speedAreaPath.lineTo(x, ySpeed)
            } else {
                speedPath.lineTo(x, ySpeed)
                speedAreaPath.lineTo(x, ySpeed)
            }

            if (index == points.size - 1) {
                speedAreaPath.lineTo(x, height)
                speedAreaPath.close()
            }
        }

        // Draw Speed gradient background fill
        drawPath(
            path = speedAreaPath,
            brush = Brush.verticalGradient(
                colors = listOf(speedColor.copy(alpha = 0.25f), Color.Transparent)
            )
        )

        // Draw Speed Stroke Line
        drawPath(
            path = speedPath,
            color = speedColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // 3. Draw Current Amps Path (Secondary line)
        val ampsPath = Path()
        points.forEachIndexed { index, point ->
            val x = (width / (points.size - 1)) * index
            // Map current Amps to vertical coordinates (-5A to 40A)
            val pctAmps = (point.amps - minAmpsVal) / ampsRange
            val yAmps = height - (pctAmps * height)

            if (index == 0) {
                ampsPath.moveTo(x, yAmps)
            } else {
                ampsPath.lineTo(x, yAmps)
            }
        }

        // Draw Amps Stroke Line (Dashed or thin)
        drawPath(
            path = ampsPath,
            color = currentColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // 4. Draw outer border
        drawLine(
            color = gridColor * 2f,
            start = Offset(0f, 0f),
            end = Offset(0f, height),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = gridColor * 2f,
            start = Offset(0f, height),
            end = Offset(width, height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

// Simple color helper multiply
private operator fun Color.times(factor: Float): Color = copy(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f)
)
