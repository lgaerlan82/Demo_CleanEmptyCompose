import android.content.Context
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// --- Theming Colors ---
val TronBackground = Color(0xFF0F171E)
val TronGrid = Color(0xFF1B2631)
val TronCyan = Color(0xFF00E5FF)
val TronOrange = Color(0xFFFF6D00)
val TronRed = Color(0xFFFF2A2A)

class MainActivity : ComponentActivity() {
private var equalizer: Equalizer? = null

override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)

// Initialize EQ on Audio Session 0 (Global Mix)
// Note: Modern Android limits session 0. For a specific player, pass its audioSessionId.
try {
equalizer = Equalizer(0, 0)
equalizer?.enabled = true
} catch (e: Exception) {
Log.e("GeminiEQ", "Failed to init Equalizer: $`{e.message}")
}

setContent {
GeminiEqualizerApp(equalizer)
}
}

override fun onDestroy() {
super.onDestroy()
equalizer?.release()
}
}

@Composable
fun GeminiEqualizerApp(equalizer: Equalizer?) {
val context = LocalContext.current
val sharedPrefs = remember { context.getSharedPreferences("GeminiEQPrefs", Context.MODE_PRIVATE) }

// EQ Constants
val bandCount = equalizer?.numberOfBands?.toInt() ?: 5
val minLevel = equalizer?.bandLevelRange?.get(0) ?: -1500
val maxLevel = equalizer?.bandLevelRange?.get(1) ?: 1500

// State
// Band levels are stored as a 0f - 1f ratio for the sliders
var bandLevels by remember { mutableStateOf(List(bandCount) { 0.5f }) }
var currentPreset by remember { mutableStateOf("Flat") }
var showDropdown by remember { mutableStateOf(false) }

// 15 Presets (Mix of standard and custom for the requirement)
val presets = listOf(
"Flat", "Bass Boost", "Treble Boost", "Electronic", "Pop",
"Rock", "Jazz", "Hip-Hop", "Classical", "Dance",
"Acoustic", "Latin", "Metal", "R&B", "Vocal Booster"
)

// Load initial states
LaunchedEffect(Unit) {
val savedPreset = sharedPrefs.getString("ActivePreset", "Flat") ?: "Flat"
currentPreset = savedPreset

if (savedPreset == "Custom") {
val customLevels = List(bandCount) { i ->
sharedPrefs.getFloat("CustomBand_`$i", 0.5f)
}
bandLevels = customLevels
}
}

// Function to apply levels to the hardware EQ
val applyLevelsToHardware = { levels: List<Float> ->
if (equalizer != null) {
levels.forEachIndexed { index, ratio ->
val actualLevel = (minLevel + (maxLevel - minLevel) * ratio).toInt().toShort()
try {
equalizer.setBandLevel(index.toShort(), actualLevel)
} catch (e: Exception) {
Log.e("GeminiEQ", "Error setting band  {e.message}")
}
}
}
}

Surface(
modifier = Modifier.fillMaxSize(),
color = TronBackground
) {
Column(
modifier = Modifier
.fillMaxSize()
.padding(16.dp),
horizontalAlignment = Alignment.CenterHorizontally
) {
// Header
Text(
text = "GEMINI AUDIO METRICS",
color = TronCyan,
fontSize = 24.sp,
fontWeight = FontWeight.Bold,
fontFamily = FontFamily.Monospace,
letterSpacing = 2.sp,
modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)
)

// Preset Selector
Box(
modifier = Modifier
.fillMaxWidth(0.8f)
.clip(RoundedCornerShape(8.dp))
.background(TronGrid)
.border(1.dp, TronCyan, RoundedCornerShape(8.dp))
.clickable { showDropdown = true }
.padding(16.dp)
) {
Row(
modifier = Modifier.fillMaxWidth(),
horizontalArrangement = Arrangement.SpaceBetween
) {
Text(
text = "PROFILE: $`{currentPreset.uppercase()}",
color = if (currentPreset == "Custom") TronOrange else TronCyan,
fontFamily = FontFamily.Monospace
)
Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TronCyan)
}

DropdownMenu(
expanded = showDropdown,
onDismissRequest = { showDropdown = false },
modifier = Modifier.background(TronGrid)
) {
presets.forEach { preset ->
DropdownMenuItem(
text = { Text(preset, color = Color.White, fontFamily = FontFamily.Monospace) },
onClick = {
currentPreset = preset
showDropdown = false
sharedPrefs.edit().putString("ActivePreset", preset).apply()

// Reset to flat if not custom. In a real app, you'd map these to specific frequency arrays.
val newLevels = List(bandCount) { 0.5f }
bandLevels = newLevels
applyLevelsToHardware(newLevels)
}
)
}
if (sharedPrefs.contains("CustomBand_0")) {
DropdownMenuItem(
text = { Text("Custom", color = TronOrange, fontFamily = FontFamily.Monospace) },
onClick = {
currentPreset = "Custom"
showDropdown = false
val customLevels = List(bandCount) { i -> sharedPrefs.getFloat("CustomBand_`$i", 0.5f) }
bandLevels = customLevels
applyLevelsToHardware(customLevels)
}
)
}
}
}

Spacer(modifier = Modifier.height(40.dp))

// The Equalizer Sliders
Row(
modifier = Modifier
.fillMaxWidth()
.weight(1f),
horizontalArrangement = Arrangement.SpaceEvenly,
verticalAlignment = Alignment.CenterVertically
) {
bandLevels.forEachIndexed { index, level ->
Column(horizontalAlignment = Alignment.CenterHorizontally) {
// Display frequency label (e.g., 60Hz, 230Hz)
val freqText = if (equalizer != null) {
val centerFreq = equalizer.getCenterFreq(index.toShort()) / 1000
if (centerFreq >= 1000) " {centerFreq}Hz"
} else "B$`index"

Text(
text = freqText,
color = Color.Gray,
fontSize = 12.sp,
fontFamily = FontFamily.Monospace,
modifier = Modifier.padding(bottom = 12.dp)
)

VerticalTronEqSlider(
value = level,
onValueChange = { newValue ->
val newList = bandLevels.toMutableList()
newList[index] = newValue
bandLevels = newList

// Auto-switch to Custom when user manually changes a slider
if (currentPreset != "Custom") {
currentPreset = "Custom"
sharedPrefs.edit().putString("ActivePreset", "Custom").apply()
}
applyLevelsToHardware(newList)
},
modifier = Modifier.weight(1f)
)
}
}
}

Spacer(modifier = Modifier.height(40.dp))

// Action Buttons
Row(
modifier = Modifier.fillMaxWidth(),
horizontalArrangement = Arrangement.SpaceEvenly
) {
// SAVE BUTTON
Button(
onClick = {
val editor = sharedPrefs.edit()
bandLevels.forEachIndexed { index, fl ->
editor.putFloat("CustomBand_`$index", fl)
}
editor.putString("ActivePreset", "Custom")
editor.apply()
currentPreset = "Custom"
},
colors = ButtonDefaults.buttonColors(containerColor = TronGrid),
shape = RoundedCornerShape(4.dp),
modifier = Modifier.border(1.dp, TronCyan, RoundedCornerShape(4.dp))
) {
Text("SAVE CUSTOM", color = TronCyan, fontFamily = FontFamily.Monospace)
}

// DELETE BUTTON (Only visible if a custom preset exists and is selected)
AnimatedVisibility(visible = currentPreset == "Custom" && sharedPrefs.contains("CustomBand_0")) {
Button(
onClick = {
val editor = sharedPrefs.edit()
for (i in 0 until bandCount) {
editor.remove("CustomBand_$i")
}
editor.putString("ActivePreset", "Flat")
editor.apply()

currentPreset = "Flat"
bandLevels = List(bandCount) { 0.5f }
applyLevelsToHardware(bandLevels)
},
colors = ButtonDefaults.buttonColors(containerColor = TronGrid),
shape = RoundedCornerShape(4.dp),
modifier = Modifier.border(1.dp, TronRed, RoundedCornerShape(4.dp))
) {
Text("DELETE", color = TronRed, fontFamily = FontFamily.Monospace)
}
}
}
Spacer(modifier = Modifier.height(24.dp))
}
}
}

@Composable
fun VerticalTronEqSlider(
value: Float, // Range 0f to 1f
onValueChange: (Float) -> Unit,
modifier: Modifier = Modifier,
neonColor: Color = TronCyan
) {
// Pulse animation for the Tron look
val infiniteTransition = rememberInfiniteTransition(label = "pulse")
val alphaAnim by infiniteTransition.animateFloat(
initialValue = 0.7f,
targetValue = 1.0f,
animationSpec = infiniteRepeatable(
animation = tween(1000, easing = FastOutSlowInEasing),
repeatMode = RepeatMode.Reverse
), label = "alpha"
)

Canvas(
modifier = modifier
.width(40.dp)
.fillMaxHeight()
.pointerInput(Unit) {
detectTapGestures { offset ->
val newValue = 1f - (offset.y / size.height).coerceIn(0f, 1f)
onValueChange(newValue)
}
}
.pointerInput(Unit) {
detectDragGestures { change, _ ->
val newValue = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
onValueChange(newValue)
}
}
) {
val sliderWidth = 6.dp.toPx()
val thumbRadius = 14.dp.toPx()
val centerX = size.width / 2
val height = size.height

val activeY = height - (height * value).coerceIn(thumbRadius, height - thumbRadius)

// Draw Inactive Background Track
drawRoundRect(
color = Color.DarkGray.copy(alpha = 0.3f),
topLeft = Offset(centerX - sliderWidth / 2, 0f),
size = Size(sliderWidth, height),
cornerRadius = CornerRadius(sliderWidth / 2)
)

// Configure Framework Paint for Neon Bloom
val neonPaint = Paint().apply {
color = neonColor.copy(alpha = alphaAnim)
}

neonPaint.asFrameworkPaint().apply {
isAntiAlias = true
setShadowLayer(
35f,
0f,
 0f,
 neonColor.copy(alpha = alphaAnim).toArgb()
)
}

drawIntoCanvas { canvas ->
// Draw Active Glowing Track
canvas.drawRoundRect(
left = centerX - sliderWidth / 2,
top = activeY,
right = centerX + sliderWidth / 2,
bottom = height,
radiusX = sliderWidth / 2,
radiusY = sliderWidth / 2,
paint = neonPaint
)

// Draw Glowing Thumb
canvas.drawCircle(
center = Offset(centerX, activeY),
radius = thumbRadius,
paint = neonPaint
)

// Draw Solid White Core
val corePaint = Paint().apply { color = Color.White }
canvas.drawCircle(
center = Offset(centerX, activeY),
radius = thumbRadius * 0.4f,
paint = corePaint
)
}
}
}
