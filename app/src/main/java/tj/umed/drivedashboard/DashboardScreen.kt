package tj.umed.drivedashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale
import kotlin.math.roundToInt

private val background = Color.Black
private val tileBackground = Color(0xFF151515)
private val tileText = Color(0xFFD9D9D9)
private val unitText = Color(0xFF818181)
private val pills = Color(0xFF242424)

/**
 * The Figma frame is exactly 1440 x 3200 design units.
 * Every element is positioned using proportional design coordinates, so its proportions
 * remain the same on Android displays with other pixel densities.
 */
fun Modifier.frameAt(x: Number, y: Number, width: Number, height: Number, scale: Float): Modifier =
    this.offset(x = (x.toFloat() * scale).dp, y = (y.toFloat() * scale).dp)
        .size((width.toFloat() * scale).dp, (height.toFloat() * scale).dp)


@Composable
private fun Tile(
    x: Int,
    y: Int,
    title: String,
    value: String,
    unit: String,
    scale: Float,
    touch: Modifier = Modifier
) {
    Box(
        Modifier.frameAt(x, y, 621, 473.2, scale)
            .clip(RoundedCornerShape((75.1f * scale).dp))
            .background(tileBackground)
            .then(touch)
    ) {
        Text(
            title.uppercase(Locale.getDefault()),
            Modifier.fillMaxWidth().offset(y = (45.38f * scale).dp),
            color = tileText,
            fontFamily = MiSansFamily, fontWeight = FontWeight.Medium,
            fontSize = (36.92f * scale).sp, lineHeight = (52.5f * scale).sp,
            textAlign = TextAlign.Center, maxLines = 1
        )
        // All number strings are <= 3 characters; the original MiSans font size is constant.
        val fontSize = 261.6f
        Text(
            value,
            Modifier.fillMaxWidth().offset(y = (51.06f * scale).dp),
            color = tileText, fontFamily = MiSansFamily, fontWeight = FontWeight.Normal,
            fontSize = (fontSize * scale).sp, lineHeight = (295f * scale).sp,
            textAlign = TextAlign.Center, maxLines = 1
        )
        Text(
            unit.uppercase(Locale.getDefault()),
            Modifier.fillMaxWidth().offset(y = (361.99f * scale).dp),
            color = unitText, fontFamily = MiSansFamily, fontWeight = FontWeight.Bold,
            fontSize = (36.92f * scale).sp, lineHeight = (54f * scale).sp,
            textAlign = TextAlign.Center, maxLines = 1
        )
    }
}

@Composable
fun DashboardScreen(
    controller: DashboardController,
    onSettings: () -> Unit,
    onRequestGps: () -> Unit,
    onBrightness: (Float) -> Unit,
    onOpenApp: (String) -> Unit
) {
    var editDistance by remember { mutableStateOf(false) }
    var distanceInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val scale = maxWidth.value / 1440f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height((3200f * scale).dp).background(background)) {
                Text(
                    controller.clock,
                    Modifier.frameAt(524, 105, 392, 183, scale),
                    color = Color.White, textAlign = TextAlign.Center,
                    fontFamily = MiSansFamily, fontWeight = FontWeight.SemiBold,
                    fontSize = (137.7f * scale).sp,
                    lineHeight = (183f * scale).sp,
                    maxLines = 1
                )

                Row(
                    Modifier.frameAt(162, 182, 201, 29, scale)
                        .clickable { onRequestGps() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val colors = listOf(Color(0xFFFF2734), Color(0xFFFF9F00),
                        Color(0xFFFFCC00), Color(0xFF8EFF00), Color(0xFF00EF30))
                    colors.forEachIndexed { index, color ->
                        Box(Modifier.size((28.59f * scale).dp).clip(CircleShape)
                            .background(if (controller.gpsBars > index) color else Color(0xFF343434)))
                    }
                }

                // Native battery indicator, sized and aligned to the Figma battery group.
                Box(
                    Modifier.frameAt(1093, 175, 71, 40.53, scale)
                        .border((2.6f * scale).dp, Color.White, RoundedCornerShape((7.6f * scale).dp))
                        .padding((5.05f * scale).dp)
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth((controller.batteryPercent / 100f).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape((2.5f * scale).dp))
                            .background(Color.White)
                    )
                }
                Box(Modifier.frameAt(1166.5, 187.7, 2.53, 15.2, scale).background(Color.White))
                Text(
                    controller.batteryPercent.toString() + "%",
                    Modifier.frameAt(1184, 170, 120, 52, scale),
                    color = Color.White, fontFamily = MiSansFamily,
                    fontWeight = FontWeight.Normal, fontSize = (39.5f * scale).sp,
                    lineHeight = (52f * scale).sp
                )

                Tile(64, 343, "Осталось", TileNumberFormatter.kilometers(controller.remainingKm), "км", scale)
                Tile(
                    755, 343, "Пройдено", TileNumberFormatter.kilometers(controller.distanceKm), "км", scale,
                    Modifier.pointerInput(controller) {
                        detectTapGestures(onLongPress = { controller.resetTripKeepingTarget() })
                    }
                )
                Tile(64, 886, "Прибытие", controller.arrivalMinutes?.let { TileNumberFormatter.integer(it) } ?: "—", "мин", scale)
                Tile(755, 886, "Прошло", TileNumberFormatter.integer((controller.elapsedMs / 60000L).toInt()), "мин", scale)

                Tile(
                    64, 1429, "Скорость", TileNumberFormatter.integer(controller.speedKmh.roundToInt()), "км/ч", scale,
                    Modifier.pointerInput(controller) {
                        detectTapGestures(
                            onDoubleTap = { controller.toggleManualPause() },
                            onLongPress = { controller.useAutomaticMode() }
                        )
                    }
                )
                Tile(
                    755, 1429, "Расстояние", TileNumberFormatter.kilometers(controller.targetKm), "км", scale,
                    Modifier.clickable {
                        distanceInput = if (controller.targetKm > 0) controller.targetKm.toString() else ""
                        editDistance = true
                    }
                )

                Text(
                    "ЯРКОСТЬ",
                    Modifier.frameAt(64, 1976, 1312, 54, scale),
                    fontSize = (37.97f * scale).sp,
                    lineHeight = (54f * scale).sp, color = tileText,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium, fontFamily = MiSansFamily
                )
                Canvas(
                    Modifier.frameAt(64, 2046.59, 1312, 170.406, scale)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                onBrightness((it.x / size.width).coerceIn(0.02f, 1f))
                                controller.saveBrightness()
                            })
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { controller.saveBrightness() },
                                onDrag = { change, _ ->
                                    onBrightness((change.position.x / size.width).coerceIn(0.02f, 1f))
                                    change.consume()
                                }
                            )
                        }
                ) {
                    // Figma layers: dark bordered black pill is BEHIND the gray pill.
                    // The gray fill starts at x=64, not inset by the border.
                    val radius = size.height / 2f // Figma 143.203, clamped to half height.
                    val border = size.height * (14.481f / 170.406f)
                    drawRoundRect(
                        color = Color(0xFF212121),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(border, border),
                        size = Size(
                            (size.width - 2f * border).coerceAtLeast(0f),
                            (size.height - 2f * border).coerceAtLeast(0f)
                        ),
                        cornerRadius = CornerRadius(
                            (radius - border).coerceAtLeast(0f),
                            (radius - border).coerceAtLeast(0f)
                        )
                    )
                    val fillWidth = size.width * controller.brightness.coerceIn(0.02f, 1f)
                    drawRoundRect(
                        color = Color(0xFFBABABA),
                        topLeft = Offset.Zero,
                        size = Size(fillWidth, size.height),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                }

                // Eight true launcher icons: the colored boxes in the Figma file are placeholders.
                controller.pinnedApps.take(8).forEachIndexed { index, pkg ->
                    val column = index % 4
                    val row = index / 4
                    val bitmap = remember(pkg) {
                        runCatching {
                            context.packageManager.getApplicationIcon(pkg)
                                .toBitmap(256, 256).asImageBitmap()
                        }.getOrNull()
                    }
                    Box(
                        Modifier.frameAt(64 + column * 352, 2320.61 + row * 328.348, 256, 256, scale)
                            .clip(RoundedCornerShape((61.217f * scale).dp))
                            .clickable { onOpenApp(pkg) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(bitmap, contentDescription = pkg, modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                Box(
                    Modifier.frameAt(495.59, 3008, 448.4, 128, scale)
                        .clip(RoundedCornerShape((64f * scale).dp))
                        .background(pills)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "НАСТРОЙКИ",
                        color = Color.White, fontFamily = MiSansFamily,
                        fontWeight = FontWeight.Normal, fontSize = (49.5f * scale).sp,
                        maxLines = 1, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (editDistance) {
        AlertDialog(
            onDismissRequest = { editDistance = false },
            title = { Text("Расстояние до конечного пункта") },
            text = {
                Column {
                    Text("Введите общую дистанцию в километрах. Изменение начинает новую поездку.")
                    Spacer(Modifier.height(12.dp))
                    BasicTextField(
                        value = distanceInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() || it == '.' || it == ',' } && input.length <= 8) {
                                distanceInput = input
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontFamily = MiSansFamily),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFF242424), RoundedCornerShape(12.dp))
                            .padding(15.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    distanceInput.replace(',', '.').toDoubleOrNull()?.let {
                        controller.setTarget(it)
                        editDistance = false
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editDistance = false }) { Text("Отмена") }
            }
        )
    }
}
