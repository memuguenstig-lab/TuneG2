package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ScooterViewModel

@Composable
fun SniffingScreen(viewModel: ScooterViewModel, modifier: Modifier = Modifier) {
    val traffic by viewModel.trafficList.collectAsState()
    val services by viewModel.discoveredServices.collectAsState()
    var commandText by remember { mutableStateOf("") }
    
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("BLE Services", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            items(services) { service ->
                Text(text = service, fontSize = 10.sp, modifier = Modifier.padding(4.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row {
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                label = { Text("Command (e.g. AA 55 ...)") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { 
                // Simple hex parser: split space, parse to Byte
                val bytes = commandText.split(" ").mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
                viewModel.sendCustomCommand(bytes)
            }) { Text("Send") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BLE Traffic", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { viewModel.clearTraffic() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Traffic")
            }
        }
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(traffic.reversed()) { packet ->
                val isOut = packet.startsWith("OUT:")
                val color = if (isOut) Color(0xFFE3F2FD) else Color(0xFFF1F8E9)
                val textColor = if (isOut) Color(0xFF1565C0) else Color(0xFF2E7D32)
                
                Card(
                    modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = color)
                ) {
                    Text(
                        text = packet,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = textColor,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
