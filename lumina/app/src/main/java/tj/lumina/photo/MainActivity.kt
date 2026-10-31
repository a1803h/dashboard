package tj.lumina.photo

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.lumina.photo.model.EditSettings
import tj.lumina.photo.model.FilmPreset
import tj.lumina.photo.model.Presets
import tj.lumina.photo.processing.PhotoProcessor
import tj.lumina.photo.storage.HistoryItem
import tj.lumina.photo.storage.PhotoStore

private val background = Color(0xFF080909)
private val surface = Color(0xFF171818)
private val raised = Color(0xFF222323)
private val cream = Color(0xFFF1DCC2)
private val dim = Color(0xFF999A99)
private val white = Color(0xFFF5F1EB)
private val border = Color(0xFF303131)

private enum class Page { HOME, EDITOR, HISTORY, BATCH }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 9, 9)
        window.navigationBarColor = android.graphics.Color.rgb(8, 9, 9)
        setContent { LuminaApp(initialSharedUri()) }
    }

    private fun initialSharedUri(): Uri? = if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    } else null
}

@Composable
private fun LuminaApp(shared: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.HOME) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var presetId by remember { mutableStateOf("portra") }
    var controls by remember { mutableStateOf(EditSettings()) }
    var category by remember { mutableStateOf("Плёнка") }
    var batchUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var batchDone by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var historyVersion by remember { mutableIntStateOf(0) }
    var showManual by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var split by remember { mutableFloatStateOf(0.50f) }

    fun load(uri: Uri) {
        photoUri = uri
        original = null
        result = null
        controls = EditSettings()
        split = 0.5f
        page = Page.EDITOR
        scope.launch {
            try {
                original = withContext(Dispatchers.IO) { PhotoStore.decode(context, uri, 1800) }
            } catch (e: Exception) { notice = "Не удалось открыть фото: ${e.localizedMessage}" }
        }
    }

    val chooseOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) load(uri)
    }
    val chooseBatch = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) { batchUris = uris; batchDone = 0; page = Page.BATCH }
    }

    LaunchedEffect(shared) { if (shared != null) load(shared) }
    LaunchedEffect(original, presetId, controls) {
        val input = original ?: return@LaunchedEffect
        delay(140)
        rendering = true
        try {
            result = withContext(Dispatchers.Default) { PhotoProcessor.render(input, Presets.get(presetId), controls) }
        } catch (e: Exception) { notice = "Ошибка обработки: ${e.localizedMessage}" }
        finally { rendering = false }
    }

    fun saveCurrent() {
        val uri = photoUri ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                val output = withContext(Dispatchers.Default) {
                    val full = PhotoStore.decode(context, uri, 4096)
                    val edited = PhotoProcessor.render(full, Presets.get(presetId), controls)
                    full.recycle()
                    edited
                }
                withContext(Dispatchers.IO) {
                    PhotoStore.saveToGallery(context, output, Presets.get(presetId).title)
                    PhotoStore.storeHistory(context, output, Presets.get(presetId).title)
                }
                output.recycle()
                historyVersion++
                notice = "Сохранено в Галерея → Pictures/Lumina"
            } catch (e: Exception) { notice = "Ошибка сохранения: ${e.localizedMessage}" }
            finally { busy = false }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = cream, background = background, surface = surface, onBackground = white, onSurface = white)) {
        Box(Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
            when (page) {
                Page.HOME -> HomePage(
                    onPick = { chooseOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onBatch = { chooseBatch.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onHistory = { page = Page.HISTORY },
                    onSettings = { showSettings = true },
                    onQuickPreset = { id -> presetId = id; category = Presets.get(id).family; chooseOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                )
                Page.EDITOR -> EditorPage(
                    original = original, edited = result, presetId = presetId, settings = controls,
                    category = category, split = split, rendering = rendering, busy = busy, manual = showManual,
                    onBack = { page = Page.HOME }, onSave = ::saveCurrent,
                    onImport = { chooseOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onPreset = { presetId = it }, onCategory = { category = it },
                    onSettings = { controls = it }, onSplit = { split = it }, onManual = { showManual = !showManual }
                )
                Page.HISTORY -> HistoryPage(
                    historyVersion = historyVersion,
                    onBack = { page = Page.HOME },
                    onClear = { PhotoStore.clearHistory(context); historyVersion++ }
                )
                Page.BATCH -> BatchPage(
                    uris = batchUris, presetId = presetId, settings = controls,
                    done = batchDone, busy = busy,
                    onBack = { if (!busy) page = Page.HOME },
                    onPreset = { presetId = it }, onSettings = { controls = it },
                    onStart = {
                        if (!busy) {
                            busy = true; batchDone = 0
                            scope.launch {
                                var failed = 0
                                for (uri in batchUris) {
                                    try {
                                        val bitmap = withContext(Dispatchers.Default) {
                                            val source = PhotoStore.decode(context, uri, 4096)
                                            val edit = PhotoProcessor.render(source, Presets.get(presetId), controls)
                                            source.recycle()
                                            edit
                                        }
                                        withContext(Dispatchers.IO) {
                                            PhotoStore.saveToGallery(context, bitmap, Presets.get(presetId).title)
                                            PhotoStore.storeHistory(context, bitmap, Presets.get(presetId).title)
                                        }
                                        bitmap.recycle()
                                    } catch (_: Exception) { failed++ }
                                    batchDone++
                                }
                                historyVersion++
                                busy = false
                                notice = "Готово: ${batchDone - failed} из $batchDone фото сохранены"
                            }
                        }
                    }
                )
            }
            if (busy && page == Page.EDITOR) {
                Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(surface).padding(10.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cream)
                        Spacer(Modifier.width(12.dp)); Text("Экспорт в полном разрешении…", color = white, fontSize = 12.sp)
                    }
                }
            }
            if (notice.isNotBlank()) {
                LaunchedEffect(notice) { delay(4200); notice = "" }
                Surface(Modifier.align(Alignment.BottomCenter).padding(18.dp), color = raised, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, border)) {
                    Text(notice, Modifier.padding(horizontal = 18.dp, vertical = 13.dp), color = white, fontSize = 12.sp)
                }
            }
        }
        if (showSettings) AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Lumina · 0.1", color = white) },
            text = { Text("Все фотографии обрабатываются на устройстве. Нет сети, генерации объектов и рекламы. JPEG сохраняется в Pictures/Lumina. Сейчас максимальная сторона экспорта — 4096 пикселей. Исходники не изменяются.", color = dim, fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Понятно", color = cream) } },
            containerColor = surface
        )
    }
}

@Composable
private fun AssetPhoto(asset: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val bitmap = remember(asset) { runCatching { context.assets.open(asset).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull() }
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = scale)
    else Box(modifier.background(raised))
}

@Composable
private fun HomePage(onPick: () -> Unit, onBatch: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, onQuickPreset: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(25.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("L U M I N A  ✦", fontSize = 20.sp, letterSpacing = 3.sp, color = cream, fontWeight = FontWeight.Medium)
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Настройки", tint = white) }
        }
        Spacer(Modifier.height(27.dp))
        Box(Modifier.fillMaxWidth().height(275.dp).clip(RoundedCornerShape(19.dp)).background(surface)) {
            AssetPhoto("hero.jpg", Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000), Color(0xF1080909)), startY = 45f)))
            Column(Modifier.align(Alignment.BottomStart).padding(19.dp)) {
                Text("Создавай атмосферу\nиз своих фото", color = white, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp)
                Spacer(Modifier.height(8.dp))
                Text("Профессиональные стили и тонкая\nобработка без изменения сюжета", fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFFCAC8C4))
                Spacer(Modifier.height(19.dp))
                Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = cream, contentColor = Color.Black), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Выбрать фото", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(29.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Быстрые режимы", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = white)
            Icon(Icons.Default.ChevronRight, null, tint = dim)
        }
        Spacer(Modifier.height(14.dp))
        val quick = listOf(Presets.get("portra"), Presets.get("dslr"), Presets.get("cinema"), Presets.get("bw"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            quick.forEach { preset ->
                Column(Modifier.weight(1f).clickable { onQuickPreset(preset.id) }) {
                    AssetPhoto(preset.thumbnail, Modifier.fillMaxWidth().aspectRatio(0.90f).clip(RoundedCornerShape(9.dp)))
                    Spacer(Modifier.height(5.dp))
                    Text(when(preset.id) { "portra" -> "Плёнка"; "dslr" -> "DSLR"; "cinema" -> "Кино"; else -> "Ч/Б" }, color = white, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        Text("Инструменты", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = white)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTool(Modifier.weight(1f), Icons.Default.Collections, "Пакетная обработка", "До 20 фото", onBatch)
            HomeTool(Modifier.weight(1f), Icons.Default.History, "История", "Готовые снимки", onHistory)
        }
        Spacer(Modifier.height(25.dp))
        Text("Локальная обработка · Без генеративного ИИ", color = dim, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun HomeTool(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(surface).clickable(onClick = onClick).padding(15.dp)) {
        Icon(icon, null, tint = cream, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(11.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = white, maxLines = 2)
        Text(description, fontSize = 10.sp, color = dim)
    }
}

@Composable
private fun EditorPage(
    original: Bitmap?, edited: Bitmap?, presetId: String, settings: EditSettings,
    category: String, split: Float, rendering: Boolean, busy: Boolean, manual: Boolean,
    onBack: () -> Unit, onSave: () -> Unit, onImport: () -> Unit,
    onPreset: (String) -> Unit, onCategory: (String) -> Unit,
    onSettings: (EditSettings) -> Unit, onSplit: (Float) -> Unit, onManual: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(57.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = white) }
            Spacer(Modifier.weight(1f))
            Text("РЕДАКТОР", fontSize = 12.sp, color = dim, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onImport) { Icon(Icons.Default.AddPhotoAlternate, "Другое фото", tint = white) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF111212)), contentAlignment = Alignment.Center) {
            when {
                original == null -> CircularProgressIndicator(color = cream)
                else -> PhotoCompare(original, edited ?: original, split, onSplit)
            }
            if (rendering) {
                Box(Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("Обработка…", fontSize = 10.sp, color = cream)
                }
            }
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniTag("До")
                MiniTag("После")
            }
        }
        Spacer(Modifier.height(7.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Presets.families) { family ->
                val selected = category == family
                Box(Modifier.clip(RoundedCornerShape(20.dp)).then(if (selected) Modifier.border(1.dp, cream, RoundedCornerShape(20.dp)) else Modifier).background(if (selected) surface else Color.Transparent).clickable { onCategory(family) }.padding(horizontal = 13.dp, vertical = 9.dp)) {
                    Text(family, fontSize = 11.sp, color = if (selected) cream else dim)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val shown = Presets.all.filter { it.family == category }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(shown, key = { it.id }) { preset -> PresetCard(preset, selected = presetId == preset.id, onClick = { onPreset(preset.id) }) }
        }
        Spacer(Modifier.height(9.dp))
        Column(Modifier.padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Интенсивность", fontSize = 12.sp, color = white)
                Spacer(Modifier.weight(1f))
                Text("${(settings.intensity * 100).toInt()}", fontSize = 12.sp, color = cream)
            }
            Slider(value = settings.intensity, onValueChange = { onSettings(settings.copy(intensity = it)) }, colors = SliderDefaults.colors(thumbColor = cream, activeTrackColor = cream, inactiveTrackColor = raised), modifier = Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth().clickable { onManual() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = dim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(if (manual) "Скрыть настройки" else "Точная настройка", color = dim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Icon(if (manual) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = dim)
            }
        }
        if (manual) {
            Column(Modifier.fillMaxWidth().heightIn(max = 202.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                CompactSlider("Зерно", settings.grain, 0f..1f, { onSettings(settings.copy(grain = it)) })
                CompactSlider("Контраст", settings.contrast, -1f..1f, { onSettings(settings.copy(contrast = it)) })
                CompactSlider("Тёплые тона", settings.warmth, -1f..1f, { onSettings(settings.copy(warmth = it)) })
                CompactSlider("Выцветание", settings.fade, 0f..1f, { onSettings(settings.copy(fade = it)) })
                CompactSlider("Насыщенность", settings.saturation, -1f..1f, { onSettings(settings.copy(saturation = it)) })
                TextButton(onClick = { onSettings(EditSettings()) }) { Text("Сбросить настройки", fontSize = 12.sp, color = dim) }
            }
        }
        Button(onClick = onSave, enabled = original != null && !busy, modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 15.dp).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = cream, contentColor = Color.Black), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(9.dp)); Text("Сохранить JPEG", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PresetCard(preset: FilmPreset, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(87.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(width = 87.dp, height = 89.dp).clip(RoundedCornerShape(9.dp)).then(if (selected) Modifier.border(2.dp, cream, RoundedCornerShape(9.dp)) else Modifier)) {
            AssetPhoto(preset.thumbnail, Modifier.fillMaxSize())
            if (selected) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).background(Color(0xC9000000), CircleShape).padding(3.dp)) {
                Icon(Icons.Default.Check, "Выбрано", tint = white, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(preset.title, fontSize = 10.sp, color = if (selected) cream else white, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniTag(text: String) {
    Box(Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xB8000000)).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(text, fontSize = 11.sp, color = white)
    }
}

@Composable
private fun PhotoCompare(source: Bitmap, processed: Bitmap, position: Float, onPosition: (Float) -> Unit) {
    val currentPosition by rememberUpdatedState(position)
    val currentOnPosition by rememberUpdatedState(onPosition)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val whole = Modifier.fillMaxSize()
        Image(source.asImageBitmap(), null, modifier = whole, contentScale = ContentScale.Fit)
        Image(
            processed.asImageBitmap(), null,
            modifier = Modifier.fillMaxSize().drawWithContent {
                clipRect(left = size.width * position, top = 0f, right = size.width, bottom = size.height) { drawContent() }
            },
            contentScale = ContentScale.Fit
        )
        Box(Modifier.fillMaxHeight().width(1.6.dp).offset(x = maxWidth * position).background(cream))
        Box(Modifier.align(Alignment.CenterStart).offset(x = maxWidth * position - 18.dp).size(36.dp).background(Color.Black, CircleShape).border(1.dp, cream, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.SwapHoriz, "Переместить сравнение", tint = white, modifier = Modifier.size(23.dp))
        }
        Box(Modifier.fillMaxSize().pointerInput(maxPx) {
            detectDragGestures { change, amount ->
                change.consume()
                currentOnPosition((currentPosition + amount.x / maxPx).coerceIn(0.04f, 0.96f))
            }
        })
    }
}

@Composable
private fun CompactSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = white, fontSize = 11.sp, modifier = Modifier.width(112.dp))
        Slider(value = value, valueRange = range, onValueChange = onChange, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = cream, activeTrackColor = cream, inactiveTrackColor = raised))
        Text("${(value * 100).toInt()}", color = dim, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(35.dp))
    }
}

@Composable
private fun BatchPage(uris: List<Uri>, presetId: String, settings: EditSettings, done: Int, busy: Boolean, onBack: () -> Unit, onPreset: (String) -> Unit, onSettings: (EditSettings) -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenHeader("Пакетная обработка", onBack)
        Spacer(Modifier.height(17.dp))
        Text("Выбрано фотографий: ${uris.size}", fontSize = 13.sp, color = dim)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uris) { uri -> UriThumbnail(uri) }
        }
        Spacer(Modifier.height(25.dp))
        Text("Режим обработки", color = white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Presets.all) { preset ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (preset.id == presetId) raised else surface).clickable(enabled = !busy) { onPreset(preset.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssetPhoto(preset.thumbnail, Modifier.size(45.dp).clip(RoundedCornerShape(7.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(preset.title, color = white, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(preset.description, color = dim, fontSize = 10.sp, maxLines = 1)
                    }
                    if (preset.id == presetId) Icon(Icons.Default.CheckCircle, null, tint = cream)
                }
            }
        }
        Text("Интенсивность: ${(settings.intensity * 100).toInt()}", color = white, fontSize = 12.sp)
        Slider(value = settings.intensity, onValueChange = { onSettings(settings.copy(intensity = it)) }, enabled = !busy, colors = SliderDefaults.colors(thumbColor = cream, activeTrackColor = cream))
        if (busy) {
            LinearProgressIndicator(progress = { done.toFloat() / uris.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth(), color = cream)
            Spacer(Modifier.height(5.dp))
            Text("Сохранено: $done / ${uris.size}", color = dim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onStart, enabled = !busy && uris.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(51.dp), colors = ButtonDefaults.buttonColors(containerColor = cream, contentColor = Color.Black), shape = RoundedCornerShape(12.dp)) {
            Text("Обработать ${uris.size} фото", fontWeight = FontWeight.SemiBold)
        }
        Text("Обработка происходит локально · Оригиналы сохраняются", color = dim, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
    }
}

@Composable
private fun UriThumbnail(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = runCatching { withContext(Dispatchers.IO) { PhotoStore.decode(context, uri, 260) } }.getOrNull()
    }
    Box(Modifier.size(73.dp).clip(RoundedCornerShape(9.dp)).background(surface)) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun HistoryPage(historyVersion: Int, onBack: () -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    val entries = remember(historyVersion) { PhotoStore.listHistory(context) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("История", onBack, trailing = {
            TextButton(onClick = { showClearConfirmation = true }, enabled = entries.isNotEmpty()) { Text("Очистить", color = cream, fontSize = 12.sp) }
        })
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.PhotoLibrary, null, tint = dim, modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(12.dp))
                Text("Пока нет обработанных фотографий", color = dim, fontSize = 13.sp)
                Text("Сохранённые снимки появятся здесь", color = dim, fontSize = 11.sp)
            }
        } else {
            Text("Сохранённые фотографии", color = dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries.chunked(3)) { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        chunk.forEach { entry -> HistoryThumb(entry, Modifier.weight(1f)) }
                        repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item { Spacer(Modifier.height(15.dp)) }
            }
        }
    }
    if (showClearConfirmation) AlertDialog(
        onDismissRequest = { showClearConfirmation = false },
        title = { Text("Очистить историю?", color = white) },
        text = { Text("Будут удалены только внутренние миниатюры Lumina. JPEG, сохранённые в Галерею, останутся.", color = dim) },
        confirmButton = { TextButton(onClick = { onClear(); showClearConfirmation = false }) { Text("Очистить", color = cream) } },
        dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("Отмена", color = dim) } },
        containerColor = surface
    )
}

@Composable
private fun HistoryThumb(entry: HistoryItem, modifier: Modifier) {
    var full by remember { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = entry.file) {
        value = withContext(Dispatchers.IO) {
            android.graphics.BitmapFactory.decodeFile(entry.file.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })
        }
    }
    Column(modifier.clickable { full = true }) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.88f).clip(RoundedCornerShape(9.dp)).background(surface)) {
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(3.dp))
        Text(entry.preset, fontSize = 10.sp, maxLines = 1, color = dim, overflow = TextOverflow.Ellipsis)
    }
    if (full) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(background).clickable { full = false }, contentAlignment = Alignment.Center) {
                val large by produceState<Bitmap?>(null, entry.file) { value = withContext(Dispatchers.IO) { PhotoStore.loadHistoryBitmap(entry.file) } }
                if (large != null) Image(large!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(onClick = { full = false }, modifier = Modifier.align(Alignment.TopStart).padding(14.dp)) { Icon(Icons.Default.Close, "Закрыть", tint = white) }
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(57.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = white) }
        Text(title, fontSize = 15.sp, color = white, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Box(Modifier.widthIn(min = 46.dp), contentAlignment = Alignment.CenterEnd) { trailing() }
    }
}
