package tj.umed.drivedashboard

import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.Locale

private val pageBg = Color(0xFF050505)
private val muted = Color(0xFF6E6E6E)
private val edge = Color(0xFF2A2A2A)
private val chipBg = Color(0xFF242424)
private val green = Color(0xFF00ED25)
private val blue = Color(0xFF63CBFF)
private val gpsColors = listOf(Color(0xFFFF322C), Color(0xFFFF7A00), Color(0xFFFFD600), Color(0xFF95FF00), Color(0xFF00ED25))

@Composable
fun DashboardScreen(
    controller: DashboardController,
    media: MediaBridge,
    audio: AudioManager,
    onSettings: () -> Unit,
    onRequestGps: () -> Unit,
    onMediaPermission: () -> Unit,
    onBrightness: (Float) -> Unit,
    onOpenApp: (String) -> Unit
) {
    var editRoute by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    val remaining = (controller.targetKm - controller.distanceKm).coerceAtLeast(0.0)
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }

    BoxWithConstraints(Modifier.fillMaxSize().background(pageBg)) {
        val widthScale = maxWidth.value / 1080f
        val heightScale = maxHeight.value / 2580.353f
        val scale = min(widthScale, heightScale)
        fun s(px: Float): Dp = (px * scale).dp
        fun spx(px: Float) = (px * scale).sp
        val designWidth = s(1080f)
        val designHeight = s(2580.353f)
        val horizontalInset = (maxWidth - designWidth) / 2f

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .offset(x = horizontalInset, y = 0.dp)
                    .size(designWidth, designHeight)
            ) {
                TopBar(
                    controller = controller,
                    onRequestGps = onRequestGps,
                    gpsTextSize = spx(55.217f),
                    dotSize = s(28.588f),
                    dotGap = s(14.823f),
                    modifier = Modifier
                        .offset(x = s(41.294f), y = s(66.706f))
                        .size(s(997.412f), s(73f))
                )

                SpeedCluster(
                    controller = controller,
                    scale = scale,
                    modifier = Modifier
                        .offset(x = s(88.941f), y = s(183.176f))
                        .size(s(873.176f), s(611f))
                )

                

                DestinationCluster(
                    controller = controller,
                    remainingKm = remaining,
                    onReset = { askReset = true },
                    onEdit = { editRoute = true },
                    scale = scale,
                    modifier = Modifier
                        .offset(x = s(41.294f), y = s(752.823f))
                        .size(s(998.471f), s(295.207f))
                )

                UtilitySliders(
                    controller = controller,
                    volume = volume,
                    onVolumeChange = {
                        volume = it
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).roundToInt(), 0)
                    },
                    onBrightness = onBrightness,
                    scale = scale,
                    modifier = Modifier
                        .offset(x = s(41.294f), y = s(1112.677f))
                        .size(s(997.412f), s(159.029f))
                )

                MediaCard(
                    media = media,
                    onMediaPermission = onMediaPermission,
                    onLaunchPlayer = { if (controller.playerPackage.isNotBlank()) onOpenApp(controller.playerPackage) },
                    hasSelectedPlayer = controller.playerPackage.isNotBlank(),
                    scale = scale,
                    modifier = Modifier
                        .offset(x = s(41.294f), y = s(1336.677f))
                        .size(s(997.412f), s(462.706f))
                )

                PinnedAppGrid(
                    packages = controller.pinnedApps,
                    onOpenApp = onOpenApp,
                    iconSize = s(194.824f),
                    columnGap = s(72.235f),
                    rowGap = s(54f),
                    modifier = Modifier
                        .offset(x = s(41.294f), y = s(1863.677f))
                        .size(s(998.471f), s(444.706f))
                )

                SmallPill(
                    title = "Settings",
                    click = onSettings,
                    textSize = spx(56f),
                    modifier = Modifier
                        .offset(x = s(402.353f), y = s(2386.588f))
                        .size(s(276.706f), s(97.412f))
                )
            }
        }
    }

    if (editRoute) {
        RouteDialog(
            current = controller.targetKm,
            onDismiss = { editRoute = false },
            onSave = { controller.setTarget(it); editRoute = false }
        )
    }
    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text("Сброс поездки") },
            text = { Text("Обнулить пройденное расстояние и время? Общая дистанция останется.") },
            confirmButton = {
                TextButton(onClick = { controller.resetTrip(); askReset = false }) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { askReset = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun TopBar(
    controller: DashboardController,
    onRequestGps: () -> Unit,
    gpsTextSize: androidx.compose.ui.unit.TextUnit,
    dotSize: Dp,
    dotGap: Dp,
    modifier: Modifier = Modifier
) {
    // Figma anchors: GPS at x=41.294; dots x=174.706; clock right x=1038.294.
    Box(modifier) {
        Text("GPS",fontSize=gpsTextSize,fontFamily=MiSansFamily,fontWeight=FontWeight.SemiBold,
            color=Color.White,
            modifier=Modifier.align(Alignment.CenterStart).clickable { onRequestGps() })
        Row(
            Modifier.offset(x=dotSize*4.665f).align(Alignment.CenterStart),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(dotGap)
        ) {
            gpsColors.forEachIndexed { index, color ->
                Box(Modifier.size(dotSize).border(1.dp,
                    if(controller.gpsBars > index) color else Color(0xFF474747),CircleShape)
                    .background(if(controller.gpsBars>index) color else Color.Transparent,CircleShape))
            }
        }
        Text(controller.clock,color=Color.White,fontSize=gpsTextSize,
            fontFamily=MiSansFamily,fontWeight=FontWeight.SemiBold,
            modifier=Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun SpeedCluster(controller: DashboardController, scale: Float, modifier: Modifier = Modifier) {
    fun s(px: Float): Dp = (px * scale).dp
    fun spx(px: Float) = (px * scale).sp
    Box(modifier) {
        Box(Modifier.offset(x=s(374.823f),y=s(9.529f)).size(s(152f),s(82f)),
            contentAlignment=Alignment.Center) {
            Text(if(controller.gpsHasFix) "${controller.currentRpm}" else "—",
                color=Color(0xFF696969),fontSize=spx(61.619f),fontFamily=MiSansFamily,
                fontWeight=FontWeight.Medium,maxLines=1)
        }
        Text("${controller.avgSpeedKmh.roundToInt()}",
            modifier=Modifier.offset(x=s(0f),y=s(242.471f)),color=blue,
            fontSize=spx(94.539f),fontFamily=MiSansFamily,fontWeight=FontWeight.SemiBold,
            lineHeight=spx(125f))
        OutlineSpeed(
            value=controller.speedKmh.roundToInt().toString(),
            tint=speedTint(controller.speedKmh),kmh=controller.speedKmh,designScale=scale,
            modifier=Modifier.offset(x=s(172.588f),y=s(0f)).size(s(557f),s(611f))
        )
        Box(Modifier.offset(x=s(795.176f),y=s(213.882f)).size(s(78f),s(182f)),
            contentAlignment=Alignment.Center) {
            Text(controller.currentGear?.number?.toString() ?: "—",
                color=Color(0xFF313131),fontFamily=MiSansFamily,fontSize=spx(137.349f),
                fontWeight=FontWeight.Normal,maxLines=1)
        }
    }
}

@Composable
private fun DestinationCluster(
    controller:DashboardController, remainingKm:Double, onReset:()->Unit,onEdit:()->Unit,
    scale:Float,modifier:Modifier=Modifier
) {
    fun s(px:Float):Dp=(px*scale).dp
    fun spx(px:Float)=(px*scale).sp
    Box(modifier) {
        FigmaMetric("Remaining",if(controller.targetKm>0) formatKm(remainingKm) else "—",
            scale,Modifier.offset(x=s(0f),y=s(0f)).size(s(185f),s(96f)),TextAlign.Start)
        FigmaMetric("Travel time",formatMinutes(controller.elapsedMs),scale,
            Modifier.offset(x=s(374.294f),y=s(0f)).size(s(250f),s(96f)),TextAlign.Center)
        FigmaMetric("Arrival In",controller.arrivalMinutes?.let { "$it min" } ?: "—",scale,
            Modifier.offset(x=s(797.412f),y=s(0f)).size(s(200f),s(96f)),TextAlign.End)
        Canvas(Modifier.offset(x=s(0f),y=s(112.235f)).size(s(997.412f),s(29.647f))) {
            val h=size.height
            drawRoundRect(Color.Black,cornerRadius=CornerRadius(h/2f))
            drawRoundRect(Color(0xFF212121),cornerRadius=CornerRadius(h/2f),
                style=Stroke(width=s(5.294f).toPx()))
            val fill=(if(controller.targetKm>0) (controller.distanceKm/controller.targetKm).toFloat()
                .coerceIn(0f,1f) else 0f)*size.width
            if(fill>0.1f) drawRoundRect(Color(0xFF009400),
                size=Size(fill,h),cornerRadius=CornerRadius(kotlin.math.min(h/2f,fill/2f)))
        }
        Text("Distance traveled ${formatKm(controller.distanceKm)}",
            modifier=Modifier.offset(x=s(0f),y=s(160.941f)),
            color=Color.White,fontSize=spx(31.888f),fontFamily=MiSansFamily,fontWeight=FontWeight.Normal)
        Box(Modifier.offset(x=s(820f),y=s(160.941f)).size(s(177f),s(42f)),
            contentAlignment=Alignment.CenterEnd) {
            Text("Total ${if(controller.targetKm>0) formatKm(controller.targetKm) else "—"}",
                color=Color.White,fontSize=spx(31.888f),fontFamily=MiSansFamily,maxLines=1)
        }
        SmallPill("Reset",onReset,spx(31.888f),
            Modifier.offset(x=s(0f),y=s(220.235f)).size(s(207.529f),s(74.971f)))
        SmallPill("Edit",onEdit,spx(31.888f),
            Modifier.offset(x=s(791f),y=s(221.192f)).size(s(207.529f),s(73.059f)))
    }
}

@Composable
private fun FigmaMetric(label:String,value:String,scale:Float,modifier:Modifier=Modifier,align:TextAlign) {
    fun s(v:Float):Dp=(v*scale).dp
    fun f(v:Float)=(v*scale).sp
    Box(modifier){
        Text(label,Modifier.fillMaxWidth(),color=Color.White,
            fontSize=f(31.888f),lineHeight=f(42f),maxLines=1,softWrap=false,
            fontFamily=MiSansFamily,fontWeight=FontWeight.Normal,textAlign=align)
        Text(value,Modifier.offset(y=s(40.235f)).fillMaxWidth(),color=Color.White,
            fontSize=f(40.012f),lineHeight=f(53f),maxLines=1,softWrap=false,
            fontFamily=MiSansFamily,fontWeight=FontWeight.SemiBold,textAlign=align)
    }
}

@Composable
private fun UtilitySliders(
    controller:DashboardController,volume:Float,onVolumeChange:(Float)->Unit,
    onBrightness:(Float)->Unit,scale:Float,modifier:Modifier=Modifier
){
    fun s(px:Float):Dp=(px*scale).dp
    fun spx(px:Float)=(px*scale).sp
    Box(modifier) {
        Box(Modifier.offset(x=s(0f),y=s(-12f)).size(s(479.647f),s(42f)),contentAlignment=Alignment.Center) {
            Text("Brightness",fontSize=spx(31.423f),fontFamily=MiSansFamily,
                fontWeight=FontWeight.Medium,color=Color.White,
                lineHeight=spx(42f),maxLines=1,softWrap=false)
        }
        Box(Modifier.offset(x=s(517.765f),y=s(-12f)).size(s(479.647f),s(42f)),contentAlignment=Alignment.Center) {
            Text("Volume",fontSize=spx(31.423f),fontFamily=MiSansFamily,
                fontWeight=FontWeight.Medium,color=Color.White)
        }
        CompactSlider(controller.brightness,onBrightness,controller::saveBrightness,scale,
            Modifier.offset(x=s(0f),y=s(54.323f)).size(s(479.647f),s(104.706f)))
        CompactSlider(volume,onVolumeChange,{},scale,
            Modifier.offset(x=s(517.765f),y=s(54.323f)).size(s(479.647f),s(104.706f)))
    }
}

private fun speedTint(kmh: Double) = when {
    kmh >= 90.0 -> Color(0xFFFF1818)
    kmh >= 80.0 -> Color(0xFFFFDA00)
    kmh > 60.0 -> Color(0xFF00FA16)
    else -> Color.White
}

@Composable
private fun OutlineSpeed(value: String, tint: Color, kmh: Double, designScale:Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val speedTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.misans_latin_semibold) ?: android.graphics.Typeface.DEFAULT_BOLD
    }
    Canvas(modifier) {
        val progress = (kmh / 120.0).toFloat().coerceIn(0f, 1f)
        val strokePx = (5.294117f + 3.2f * progress) * designScale * density
        val glow = ((kmh - 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
        val mainPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = tint.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeWidth = strokePx
            typeface = speedTypeface
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 460.545f * designScale * density
        }
        val baseline = (size.height - mainPaint.ascent() - mainPaint.descent()) / 2f
        val availableWidth = (size.width - strokePx * 6f).coerceAtLeast(1f)
        val scaleX = (availableWidth / mainPaint.measureText(value).coerceAtLeast(1f)).coerceAtMost(1f)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(scaleX, 1f, size.width / 2f, baseline)
            if (glow > 0f) {
                for (layer in 4 downTo 1) {
                    val halo = android.graphics.Paint(mainPaint).apply {
                        color = tint.copy(alpha = glow * (0.035f + (5 - layer) * 0.02f)).toArgb()
                        strokeWidth = strokePx + (layer * 6f + 4f) * designScale * density
                    }
                    native.drawText(value, size.width / 2f, baseline, halo)
                }
            }
            native.drawText(value, size.width / 2f, baseline, mainPaint)
            native.restore()
        }
    }
}

@Composable
private fun TripMetric(
    label: String,
    value: String,
    labelSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal
) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(label, color = Color(0xFFE2E2E2), fontSize = labelSize)
        Text(value, color = Color.White, fontSize = valueSize, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallPill(
    title: String,
    click: () -> Unit,
    textSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(chipBg)
            .clickable(onClick = click),
        contentAlignment = Alignment.Center
    ) {
        Text(title, fontSize = textSize, color = Color(0xFFD0D0D0))
    }
}

@Composable
private fun CompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onFinish: () -> Unit = {},
    scale: Float,
    modifier: Modifier = Modifier
) {
    val fill = Color(0xFFBABABA)
    val outline = Color(0xFF212121)
    Canvas(
        modifier
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun update(x: Float) {
                        onValueChange((x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f))
                    }
                    update(down.position.x)
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            update(change.position.x)
                            change.consume()
                        }
                        pressed = change?.pressed == true
                    }
                    onFinish()
                }
            }
    ) {
        val height = size.height
        val top = (size.height - height) / 2f
        val corner = height / 2f
        drawRoundRect(
            color = outline,
            topLeft = Offset(0f, top),
            size = Size(size.width, height),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 5.294f * density * scale)
        )
        val filledWidth = size.width * value.coerceIn(0f, 1f)
        if (filledWidth > 0.5f) {
            drawRoundRect(
                color = fill,
                topLeft = Offset(0f, top),
                size = Size(filledWidth, height),
                cornerRadius = CornerRadius(min(corner, filledWidth / 2f), corner)
            )
        }
    }
}

@Composable
private fun PlaybackProgress(
    progress: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier.pointerInput(enabled) {
            if (enabled) awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun update(x: Float) { onChange((x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)) }
                update(down.position.x)
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null) {
                        update(change.position.x)
                        change.consume()
                    }
                    pressed = change?.pressed == true
                }
            }
        }
    ) {
        val height = size.height
        val radius = height / 2f
        drawRoundRect(
            color = Color(0xFF333333),
            topLeft = Offset.Zero,
            size = Size(size.width, height),
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = Color(0xFF777777),
            topLeft = Offset.Zero,
            size = Size(size.width, height),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 1.dp.toPx())
        )
        val filled = size.width * progress.coerceIn(0f, 1f)
        if (filled > 0.5f) {
            drawRoundRect(
                color = Color(0xFFBDBDBD),
                topLeft = Offset.Zero,
                size = Size(filled, height),
                cornerRadius = CornerRadius(min(radius, filled / 2f), radius)
            )
        }
    }
}

/** Figma media frame: 997.412 × 462.706 with exact grouped SVG artwork exported from Figma. */
@Composable
private fun MediaCard(
    media:MediaBridge,
    onMediaPermission:()->Unit,
    onLaunchPlayer:()->Unit,
    hasSelectedPlayer:Boolean,
    scale:Float,
    modifier:Modifier=Modifier
) {
    fun d(px:Float):Dp=(px*scale).dp
    fun f(px:Float)=(px*scale).sp
    val m=media.state
    Box(modifier.border(d(5.294f),Color(0xFF212121),RoundedCornerShape(d(54f)))) {
        Box(Modifier.offset(y=d(47.647f)).fillMaxWidth().height(d(64f)),
            contentAlignment=Alignment.Center) {
            Text(m.title,maxLines=1,color=Color.White,fontFamily=MiSansFamily,
                fontWeight=FontWeight.SemiBold,fontSize=f(48.629f),
                modifier=Modifier.clickable(
                    enabled = !media.hasNotificationAccess || hasSelectedPlayer
                ) {
                    if(!media.hasNotificationAccess) onMediaPermission()
                    else onLaunchPlayer()
                })
        }
        Box(Modifier.offset(y=d(110.117f)).fillMaxWidth().height(d(49f)),
            contentAlignment=Alignment.Center) {
            Text(m.artist,maxLines=1,color=Color(0xFF808080),fontFamily=MiSansFamily,
                fontWeight=FontWeight.Normal,fontSize=f(37.059f))
        }
        PlaybackProgress(
            progress=if(m.durationMs>0) (m.positionMs.toFloat()/m.durationMs).coerceIn(0f,1f) else 0f,
            enabled=m.available && m.durationMs>0,onChange={media.seek(it)},
            modifier=Modifier.offset(x=d(33.882f),y=d(182.118f)).size(d(929.647f),d(29.647f))
        )
        Row(Modifier.offset(x=d(33.882f),y=d(221.294f)).width(d(926.235f)),
            horizontalArrangement=Arrangement.SpaceBetween) {
            Text(timeForMs(m.positionMs),color=Color(0xFF808080),fontSize=f(21.176f),lineHeight=f(28f),maxLines=1,softWrap=false,fontFamily=MiSansFamily)
            Text(timeForMs(m.durationMs),color=Color(0xFF808080),fontSize=f(21.176f),lineHeight=f(28f),maxLines=1,softWrap=false,fontFamily=MiSansFamily)
        }
        // Pixel-exact Figma vector artwork; hit regions remain independent Android controls.
        Box(Modifier.offset(x=d(99.529f),y=d(252f)).size(d(794.891f),d(147.176f))) {
            Image(
                painter=painterResource(R.drawable.figma_media_controls),
                contentDescription=null,contentScale=ContentScale.FillBounds,
                modifier=Modifier.fillMaxSize()
            )
            if(m.playing) {
                Canvas(Modifier.offset(x=d(363f),y=d(10f)).size(d(109f),d(128f))) {
                    drawRect(pageBg)
                    drawRoundRect(Color.White,
                        topLeft=Offset(size.width*.16f,size.height*.12f),
                        size=Size(size.width*.25f,size.height*.77f),
                        cornerRadius=CornerRadius(size.width*.06f))
                    drawRoundRect(Color.White,
                        topLeft=Offset(size.width*.59f,size.height*.12f),
                        size=Size(size.width*.25f,size.height*.77f),
                        cornerRadius=CornerRadius(size.width*.06f))
                }
            }
            Box(Modifier.offset(x=d(0f)).size(d(82f),d(147f)).clickable(enabled=m.available&&m.canShuffle) {media.shuffle()})
            Box(Modifier.offset(x=d(155f)).size(d(140f),d(147f)).clickable(enabled=m.available) {media.skipPrev()})
            Box(Modifier.offset(x=d(328f)).size(d(170f),d(147f)).clickable(enabled=m.available) {media.playPause()})
            Box(Modifier.offset(x=d(500f)).size(d(146f),d(147f)).clickable(enabled=m.available) {media.skipNext()})
            Box(Modifier.offset(x=d(704f)).size(d(91f),d(147f)).clickable(enabled=m.available&&m.canRepeat) {media.repeat()})
        }
        // The player launch/permission action lives on the song title.
        // No extra action text obscures the Figma player progress and timestamps.
    }
}

@Composable
private fun PinnedAppGrid(
    packages: List<String>,
    onOpenApp: (String) -> Unit,
    iconSize: Dp,
    columnGap: Dp,
    rowGap: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (packages.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(rowGap)) {
        for (row in 0..1) {
            if (row * 4 >= packages.size) break
            Row(horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                for (column in 0..3) {
                    val pkg = packages.getOrNull(row * 4 + column)
                    Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                        if (pkg != null) {
                            val image = remember(pkg) {
                                runCatching {
                                    context.packageManager.getApplicationIcon(pkg).toBitmap(200, 200).asImageBitmap()
                                }.getOrNull()
                            }
                            if (image != null) {
                                val label = remember(pkg) {
                                    runCatching {
                                        context.packageManager.getApplicationLabel(
                                            context.packageManager.getApplicationInfo(pkg, 0)
                                        ).toString()
                                    }.getOrDefault(pkg)
                                }
                                Image(
                                    bitmap = image,
                                    contentDescription = label,
                                    modifier = Modifier.fillMaxSize().clickable { onOpenApp(pkg) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteDialog(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    val number = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Общая дистанция") },
        text = {
            Column {
                Text("Укажи длину поездки в километрах.", fontSize = 13.sp)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Км") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = number != null && number >= 0 && number <= 50000, onClick = { onSave(number!!) }) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun formatKm(n: Double): String = String.format(Locale.US, "%.1f km", n.coerceAtLeast(0.0))
private fun formatMinutes(ms: Long): String = "${ms / 60000L} min"
private fun timeForMs(ms: Long): String = "%02d:%02d".format(Locale.US, ms.coerceAtLeast(0) / 60000, (ms.coerceAtLeast(0) / 1000) % 60)