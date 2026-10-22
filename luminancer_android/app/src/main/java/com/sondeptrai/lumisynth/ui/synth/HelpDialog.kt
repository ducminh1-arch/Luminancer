package com.sondeptrai.lumisynth.ui.synth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Cyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LUMINANCER GUIDE",
                            color = Color.Cyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "ANALOG VIDEO SYNTHESIZER",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                // Gestures
                Text(
                    text = "GESTURE CONTROLS",
                    color = Color.Yellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                HelpRow(title = "1-FINGER PAN", desc = "Controls feedback trails drift offset & direction")
                HelpRow(title = "PINCH", desc = "Zooms recursive feedback loop in / out")
                HelpRow(title = "2-FINGER ROTATE", desc = "Twists and spins feedback trails")
                HelpRow(title = "DOUBLE TAP", desc = "Resets zoom, rotation, and drift to default")

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                // Synth Controls
                Text(
                    text = "SYNTH PARAMETERS",
                    color = Color(0xFFFF9800),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                HelpRow(title = "THRESHOLD", desc = "Luminance cutoff limit for light overload")
                HelpRow(title = "DECAY", desc = "Trail persistence and lingering decay rate")
                HelpRow(title = "TRAILS", desc = "Intensity of recursive feedback framebuffers")
                HelpRow(title = "CHAN / COLOR", desc = "Light detection and color strobe modes")
                HelpRow(title = "BLEND", desc = "Composites synth output with source video")

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "RESUME SYNTHESIS",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpRow(title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            color = Color.Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = desc,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp
        )
    }
}
