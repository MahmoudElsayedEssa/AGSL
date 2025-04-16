package com.binissa.agsl.globe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.binissa.agsl.R

@Composable
fun GlobeEffectScreen() {
    // Shared state that persists across tab changes
    val effectState = remember { GlobeEffectState() }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = MOTION, 1 = VISUAL
    var isHidden by remember { mutableStateOf(false) }

    // Load the image
    val context = LocalContext.current
    val imageBitmap = remember {
        context.resources.getDrawable(R.drawable.flower2, null).toBitmap().asImageBitmap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), contentAlignment = Alignment.Center
        ) {
            GlobeEffect(
                imageBitmap = imageBitmap,
                modifier = Modifier
                    .fillMaxSize(0.9f)
                    .aspectRatio(1f),
                state = effectState
            )
        }

        // Bottom controls
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Hide/Reset buttons
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Hide",
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { isHidden = !isHidden })
                Text(
                    text = "Reset",
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { effectState.reset() })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tab selector
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF333333)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp)
                ) {
                    // Motion tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 0 },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedTab == 0) Color.White else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (selectedTab == 0) Color.Black else Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = "MOTION",
                                color = if (selectedTab == 0) Color.Black else Color.White
                            )
                        }
                    }

                    // Visual tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedTab == 1) Color.White else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (selectedTab == 1) Color.Black else Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = "VISUAL",
                                color = if (selectedTab == 1) Color.Black else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls based on selected tab
            if (selectedTab == 0) {
                // MOTION controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Animate", color = Color.White)
                    Switch(
                        checked = effectState.animate,
                        onCheckedChange = { effectState.animate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.Green,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                SliderControl(
                    label = "Speed",
                    value = effectState.speed,
                    onValueChange = { effectState.speed = it },
                    valueRange = 0.01f..2f,  // Increased range for better visibility
                    valueText = effectState.speed.format(2)
                )

                SliderControl(
                    label = "Strength",
                    value = effectState.strength,
                    onValueChange = { effectState.strength = it },
                    valueRange = 0.01f..1f,
                    valueText = effectState.strength.format(2)
                )

                SliderControl(
                    label = "Frequency",
                    value = effectState.frequency,
                    onValueChange = { effectState.frequency = it },
                    valueRange = 0.01f..1f,
                    valueText = effectState.frequency.format(2)
                )

                SliderControl(
                    label = "Noise",
                    value = effectState.noise,
                    onValueChange = { effectState.noise = it },
                    valueRange = 0f..1f,
                    valueText = effectState.noise.format(2)
                )
            } else {
                // VISUAL controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Refraction", color = Color.White)
                    Switch(
                        checked = effectState.refraction,
                        onCheckedChange = { effectState.refraction = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.Green,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                SliderControl(
                    label = "Edge",
                    value = effectState.edge,
                    onValueChange = { effectState.edge = it },
                    valueRange = 0.01f..1f,
                    valueText = effectState.edge.format(2)
                )

                SliderControl(
                    label = "Light",
                    value = effectState.light,
                    onValueChange = { effectState.light = it },
                    valueRange = 0.01f..1f,
                    valueText = effectState.light.format(2)
                )

                SliderControl(
                    label = "Glow",
                    value = effectState.glow,
                    onValueChange = { effectState.glow = it },
                    valueRange = 0.01f..1f,
                    valueText = effectState.glow.format(2)
                )

                SliderControl(
                    label = "Lens",
                    value = effectState.lens,
                    onValueChange = { effectState.lens = it },
                    valueRange = 0f..1f,
                    valueText = effectState.lens.format(2)
                )
            }
        }
    }
}


fun Float.format(digits: Int) = "%.${digits}f".format(this)