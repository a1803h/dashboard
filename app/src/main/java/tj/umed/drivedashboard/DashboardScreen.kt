package tj.umed.drivedashboard

import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt
import java.util.Locale

private val pageBg = Color(0xFF050505)
private val muted = Color(0xFF858585)
private val edge = Color(0xFF252525)
private val chipBg = Color(0xFF242424)
private val green = Color(0xFF00ED25)
private val blue = Color(0xFF63CBFF)
private val tileColors = listOf(Color.Red,Color(0xFFFFC400),Color(0xFF008AFF),green,green,Color(0xFF008AFF),Color(0xFFFFC400),Color.Red)

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
    Column(
        Modifier.fillMaxSize().background(pageBg).verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.clickable { onRequestGps() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("GPS", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.width(6.dp))
                listOf(Color(0xFFFF322C),Color(0xFFFF9D13),Color.Yellow,Color(0xFF9AFF00),Color.Green).forEachIndexed { i, c ->
                    Box(Modifier.padding(end = 3.dp).size(8.dp).border(1.dp, if (controller.gpsBars > i) c else Color(0xFF474747), CircleShape)
                        .background(if (controller.gpsBars > i) c else Color.Transparent, CircleShape))
                }
            }
            Text(controller.clock, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.fillMaxWidth().height(183.dp)) {
            Text(if (controller.gpsHasFix) "${controller.currentRpm}" else "—",
                Modifier.align(Alignment.TopCenter).padding(top=8.dp), color = muted, fontSize = 17.sp)
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${controller.avgSpeedKmh.roundToInt()}", color = blue, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("AVG", color=muted, fontSize=10.sp)
                }
            }
            OutlineSpeed(controller.speedKmh.roundToInt().toString(), speedTint(controller.speedKmh), controller.speedKmh,
                Modifier.fillMaxWidth(.75f).align(Alignment.Center).height(140.dp))
            Text(controller.currentGear?.number?.toString() ?: "—",
                Modifier.align(Alignment.CenterEnd), color = Color(0xFF707070), fontSize = 43.sp, fontWeight = FontWeight.Light)
        }
        if (!controller.locationGranted) {
            TextButton(onClick = onRequestGps) { Text("Разрешить доступ к GPS", color = blue, fontSize = 12.sp) }
        } else if (!controller.gpsEnabled) {
            Text("Включите GPS в настройках телефона", color = Color(0xFFFFCA3A), fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            TripMetric("Remaining", if (controller.targetKm > 0) formatKm(remaining) else "—", Alignment.Start)
            TripMetric("Travel time", formatMinutes(controller.elapsedMs), Alignment.CenterHorizontally)
            TripMetric("Arrival In", controller.arrivalMinutes?.let { "$it min" } ?: "—", Alignment.End)
        }
        Spacer(Modifier.height(9.dp))
        LinearProgressIndicator(
            progress = { if (controller.targetKm > 0) (controller.distanceKm / controller.targetKm).toFloat().coerceIn(0f,1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(10.dp)),
            color = green, trackColor = edge
        )
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Distance traveled ${formatKm(controller.distanceKm)}", color=Color.LightGray,fontSize=10.sp)
            Text("Total ${if(controller.targetKm > 0) formatKm(controller.targetKm) else "—"}",color=Color.LightGray,fontSize=10.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallPill("Reset") { askReset = true }
            SmallPill("Edit") { editRoute = true }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment=Alignment.CenterHorizontally) {
                Text("Brightness",fontSize=11.sp,color=Color.White)
                CompactSlider(value=controller.brightness,onValueChange=onBrightness,onFinish=controller::saveBrightness)
            }
            Column(Modifier.weight(1f), horizontalAlignment=Alignment.CenterHorizontally) {
                Text("Volume",fontSize=11.sp,color=Color.White)
                CompactSlider(value=volume,onValueChange={
                    volume=it
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC,(it*maxVolume).roundToInt(),0)
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        MediaCard(media = media, onMediaPermission = onMediaPermission,
            onLaunchPlayer = { if (controller.playerPackage.isNotBlank()) onOpenApp(controller.playerPackage) },
            hasSelectedPlayer = controller.playerPackage.isNotBlank())
        Spacer(Modifier.height(18.dp))
        PinnedAppGrid(controller.pinnedApps, onOpenApp, onSettings)
        Spacer(Modifier.height(25.dp))
        SmallPill("Settings",onSettings)
    }
    if (editRoute) RouteDialog(controller.targetKm, onDismiss={editRoute=false}, onSave={controller.setTarget(it);editRoute=false})
    if (askReset) AlertDialog(onDismissRequest={askReset=false}, title={Text("Сброс поездки")},
        text={Text("Обнулить пройденное расстояние и время? Общая дистанция останется.")},
        confirmButton={TextButton(onClick={controller.resetTrip();askReset=false}){Text("Сбросить")}},
        dismissButton={TextButton(onClick={askReset=false}){Text("Отмена")}})
}

private fun speedTint(kmh: Double) = when {
    kmh >= 90.0 -> Color(0xFFFF1818)
    kmh >= 80.0 -> Color(0xFFFFDA00)
    kmh > 60.0 -> Color(0xFF00FA16)
    else -> Color.White
}

@Composable
private fun OutlineSpeed(value: String, tint: Color, kmh: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val progress = (kmh / 120.0).toFloat().coerceIn(0f, 1f)
        val strokePx = (3.1f + 2.4f * progress) * density
        val glow = ((kmh - 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
        val typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        val mainPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = tint.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeWidth = strokePx
            this.typeface = typeface
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = size.height * if (value.length >= 3) 0.79f else 1.02f
        }
        val baseline = (size.height - mainPaint.ascent() - mainPaint.descent()) / 2f
        val availableWidth = (size.width - strokePx * 5f).coerceAtLeast(1f)
        val scaleX = (availableWidth / mainPaint.measureText(value).coerceAtLeast(1f))
            .coerceAtMost(1f)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(scaleX, 1f, size.width / 2f, baseline)
            if (glow > 0f) {
                for (layer in 3 downTo 1) {
                    val halo = android.graphics.Paint(mainPaint).apply {
                        color = tint.copy(alpha = glow * (0.04f + (4 - layer) * 0.025f)).toArgb()
                        strokeWidth = strokePx + (layer * 5f + 3f) * density
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
private fun TripMetric(label:String, value:String, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment=alignment) {
        Text(label, color=Color.LightGray,fontSize=10.sp)
        Text(value,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun SmallPill(title:String, click:()->Unit) {
    Box(Modifier.clip(CircleShape).background(chipBg).clickable(onClick=click)
        .defaultMinSize(minWidth=68.dp,minHeight=30.dp).padding(horizontal=13.dp,vertical=6.dp),contentAlignment=Alignment.Center) {
        Text(title,fontSize=12.sp,color=Color.LightGray)
    }
}

@Composable
private fun CompactSlider(value:Float,onValueChange:(Float)->Unit,onFinish:()->Unit={}) {
    Slider(value = value.coerceIn(0f,1f), onValueChange=onValueChange, onValueChangeFinished=onFinish,
        modifier=Modifier.fillMaxWidth().height(38.dp),
        colors=SliderDefaults.colors(thumbColor=Color(0xFFBFBFBF),activeTrackColor=Color(0xFFBFBFBF),inactiveTrackColor=edge))
}

@Composable
private fun MediaCard(media: MediaBridge,onMediaPermission:()->Unit,
    onLaunchPlayer:()->Unit,hasSelectedPlayer:Boolean) {
    val m=media.state
    Column(Modifier.fillMaxWidth().border(1.25.dp, edge, RoundedCornerShape(17.dp))
        .padding(horizontal=12.dp,vertical=11.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(m.title,maxLines=1,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
        Text(m.artist,maxLines=1,color=muted,fontSize=12.sp)
        Spacer(Modifier.height(7.dp))
        Slider(value=if(m.durationMs>0)(m.positionMs.toFloat()/m.durationMs).coerceIn(0f,1f)else 0f,
            onValueChange={media.seek(it)},enabled=m.available&&m.durationMs>0,
            modifier=Modifier.fillMaxWidth().height(22.dp),
            colors=SliderDefaults.colors(activeTrackColor=Color.LightGray,inactiveTrackColor=edge,thumbColor=Color.LightGray))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Text(timeForMs(m.positionMs),color=muted,fontSize=9.sp)
            Text(timeForMs(m.durationMs),color=muted,fontSize=9.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
            IconButton(onClick={media.shuffle()},enabled=m.available&&m.canShuffle,modifier=Modifier.size(36.dp)) {
                Icon(Icons.Default.Shuffle,"Shuffle",tint=if(m.available&&m.canShuffle) Color.LightGray else Color.DarkGray)
            }
            IconButton(onClick={media.skipPrev()},enabled=m.available,modifier=Modifier.size(38.dp)) {
                Icon(Icons.Default.SkipPrevious,"Previous",tint=Color.White)
            }
            IconButton(onClick={media.playPause()},enabled=m.available,modifier=Modifier.size(48.dp)) {
                Icon(if(m.playing) Icons.Default.Pause else Icons.Default.PlayArrow,"Play/Pause",tint=Color.White,modifier=Modifier.size(44.dp))
            }
            IconButton(onClick={media.skipNext()},enabled=m.available,modifier=Modifier.size(38.dp)) {
                Icon(Icons.Default.SkipNext,"Next",tint=Color.White)
            }
            IconButton(onClick={media.repeat()},enabled=m.available&&m.canRepeat,modifier=Modifier.size(36.dp)) {
                Icon(Icons.Default.Repeat,"Repeat",tint=if(m.available&&m.canRepeat) Color.LightGray else Color.DarkGray)
            }
        }
        if (!media.hasNotificationAccess) {
            TextButton(onClick=onMediaPermission) {
                Text("Разрешить управление медиаплеером",color=blue,fontSize=11.sp)
            }
        } else if (!m.available) {
            if (hasSelectedPlayer) {
                TextButton(onClick=onLaunchPlayer) {
                    Text("Открыть выбранный плеер",color=blue,fontSize=11.sp)
                }
            } else Text("Нет активного медиаплеера",fontSize=10.sp,color=muted)
        }
    }
}

@Composable
private fun PinnedAppGrid(packages:List<String>,onOpenApp:(String)->Unit,onSelect:()->Unit) {
    val context=LocalContext.current
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        for (r in 0..1) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                for (c in 0..3) {
                    val index=r*4+c
                    val pkg=packages.getOrNull(index)
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(13.dp)).background(tileColors[index])
                        .clickable { if(pkg==null) onSelect() else onOpenApp(pkg) },contentAlignment=Alignment.Center) {
                        if (pkg==null) {
                            Text("App\nicon",color=Color.White,fontSize=13.sp,fontWeight=FontWeight.SemiBold,lineHeight=17.sp)
                        } else {
                            val bmp=remember(pkg) { runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96,96).asImageBitmap() }.getOrNull() }
                            val name=remember(pkg){runCatching {
                                val info=context.packageManager.getApplicationInfo(pkg,0)
                                context.packageManager.getApplicationLabel(info).toString()
                            }.getOrDefault("App")}
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                if (bmp!=null) Image(bmp,name,Modifier.size(36.dp))
                                Text(name,color=Color.White,fontSize=8.sp,maxLines=1,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteDialog(current:Double,onDismiss:()->Unit,onSave:(Double)->Unit) {
    var text by remember { mutableStateOf(if(current>0) current.toString() else "") }
    val number=text.replace(',','.').toDoubleOrNull()
    AlertDialog(onDismissRequest=onDismiss,title={Text("Общая дистанция")},
        text={Column { Text("Укажи длину поездки в километрах.",fontSize=13.sp)
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(value=text,onValueChange={text=it},label={Text("Км")},singleLine=true)
        }},confirmButton={TextButton(enabled=number!=null&&number>=0&&number<=50000,onClick={onSave(number!!)}){Text("Сохранить")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
}

private fun formatKm(n:Double):String = String.format(Locale.US,"%.1f km",n.coerceAtLeast(0.0))
private fun formatMinutes(ms:Long):String = "${ms / 60000L} min"
private fun timeForMs(ms:Long):String = "%02d:%02d".format(Locale.US,ms.coerceAtLeast(0)/60000,(ms.coerceAtLeast(0)/1000)%60)
