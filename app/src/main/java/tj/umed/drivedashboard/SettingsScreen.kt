package tj.umed.drivedashboard

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

private data class InstalledApp(val packageName: String, val title: String)

private fun launcherApps(context: Context): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val manager = context.packageManager
    @Suppress("DEPRECATION")
    val found = manager.queryIntentActivities(intent, 0)
    return found.map { InstalledApp(it.activityInfo.packageName, it.loadLabel(manager).toString()) }
        .filterNot { it.packageName == context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.title.lowercase() }
}

@Composable
fun SettingsScreen(controller: DashboardController, onBack: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { launcherApps(context) }
    var selecting by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val s = maxWidth.value / 1440f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height((840f * s).dp)) {
                Text(
                    "Настройки",
                    Modifier.frameAt(0, 154, 1440, 193, s),
                    fontSize = (145.78f * s).sp,
                    lineHeight = (193f * s).sp,
                    fontWeight = FontWeight.Light, fontFamily = MiSansFamily,
                    color = Color.White, textAlign = TextAlign.Center, maxLines = 1
                )
                Icon(
                    Icons.Filled.ArrowBack, contentDescription = "Назад",
                    tint = Color.White,
                    modifier = Modifier.frameAt(63, 208, 100, 90, s).clickable(onClick = onBack)
                )
                Text(
                    "Приложения",
                    Modifier.frameAt(63.45, 457.85, 650, 129, s),
                    color = Color.White, fontFamily = MiSansFamily,
                    fontWeight = FontWeight.Normal, fontSize = (97.62f * s).sp,
                    lineHeight = (129f * s).sp, maxLines = 1
                )
                SettingsPill("Выбрать", 63f, 622.38f, 363.19f, 130.62f, s) { selecting = true }
                SettingsPill("Сортировать", 473.74f, 622.93f, 493.58f, 129.52f, s) { sorting = true }
            }

            // No fixed-height container: the descriptions wrap and the entire page scrolls.
            Column(
                    Modifier.fillMaxWidth().padding(
                        start = (63.45f * s).dp,
                        end = (63.45f * s).dp,
                        top = (100f * s).dp,
                        bottom = (180f * s).dp
                    ),
                    verticalArrangement = Arrangement.spacedBy((58f * s).dp)
                ) {
                    Text(
                        "Управление плитками",
                        color = Color.White, fontSize = (78f * s).sp,
                        lineHeight = (98f * s).sp,
                        fontFamily = MiSansFamily, fontWeight = FontWeight.Normal
                    )
                    Column(verticalArrangement = Arrangement.spacedBy((17f * s).dp)) {
                        Text(
                            "СКОРОСТЬ",
                            color = Color(0xFFD9D9D9), fontFamily = MiSansFamily,
                            fontSize = (48f * s).sp, fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Двойное нажатие — ручная пауза (тёмно-красный фон) или " +
                                "принудительное продолжение (тёмно-жёлтый фон). " +
                                "Долгое нажатие — автоматический режим (обычный фон).",
                            color = Color(0xFF999999), fontFamily = MiSansFamily,
                            fontSize = (43f * s).sp, lineHeight = (64f * s).sp
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy((17f * s).dp)) {
                        Text(
                            "ПРОЙДЕНО",
                            color = Color(0xFFD9D9D9), fontFamily = MiSansFamily,
                            fontSize = (48f * s).sp, fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Двойное или долгое нажатие — сброс только счётчика «Пройдено». " +
                                "Он учитывает ходьбу и продолжает работать даже при паузе записи поездки. " +
                                "Время и расстояние до пункта назначения не обнуляются.",
                            color = Color(0xFF999999), fontFamily = MiSansFamily,
                            fontSize = (43f * s).sp, lineHeight = (64f * s).sp
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy((17f * s).dp)) {
                        Text(
                            "РАССТОЯНИЕ",
                            color = Color(0xFFD9D9D9), fontFamily = MiSansFamily,
                            fontSize = (48f * s).sp, fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Одно нажатие — ввод расстояния. «Сохранить» начинает новый маршрут, " +
                                "но сохраняет счётчик «Пройдено». «Корректировка» изменяет только " +
                                "расстояние, сохраняя остальные показания.",
                            color = Color(0xFF999999), fontFamily = MiSansFamily,
                            fontSize = (43f * s).sp, lineHeight = (64f * s).sp
                        )
                    }
                }
        }
    }
    if (selecting) {
        AlertDialog(
            onDismissRequest = { selecting = false },
            title = { Text("Выбрать приложения (" + controller.pinnedApps.size + "/8)") },
            text = {
                LazyColumn(Modifier.heightIn(max = 450.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val current = controller.pinnedApps
                                if (app.packageName in current) controller.setPinned(current - app.packageName)
                                else if (current.size < 8) controller.setPinned(current + app.packageName)
                            }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SelectedIcon(app.packageName)
                            Spacer(Modifier.width(10.dp))
                            Text(app.title, Modifier.weight(1f), maxLines = 1, fontSize = 14.sp)
                            Checkbox(
                                checked = app.packageName in controller.pinnedApps,
                                enabled = app.packageName in controller.pinnedApps || controller.pinnedApps.size < 8,
                                onCheckedChange = {
                                    val current = controller.pinnedApps
                                    if (app.packageName in current) controller.setPinned(current - app.packageName)
                                    else if (current.size < 8) controller.setPinned(current + app.packageName)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selecting = false }) { Text("Готово") } }
        )
    }
    if (sorting) {
        AlertDialog(
            onDismissRequest = { sorting = false },
            title = { Text("Порядок ярлыков") },
            text = {
                Column {
                    if (controller.pinnedApps.isEmpty()) Text("Сначала выберите приложения")
                    controller.pinnedApps.forEachIndexed { index, pkg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SelectedIcon(pkg)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                apps.firstOrNull { it.packageName == pkg }?.title ?: pkg,
                                Modifier.weight(1f), maxLines = 1, fontSize = 13.sp
                            )
                            IconButton(
                                onClick = {
                                    val reordered = controller.pinnedApps.toMutableList()
                                    java.util.Collections.swap(reordered, index, index - 1)
                                    controller.setPinned(reordered)
                                },
                                enabled = index > 0
                            ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Выше") }
                            IconButton(
                                onClick = {
                                    val reordered = controller.pinnedApps.toMutableList()
                                    java.util.Collections.swap(reordered, index, index + 1)
                                    controller.setPinned(reordered)
                                },
                                enabled = index < controller.pinnedApps.lastIndex
                            ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Ниже") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sorting = false }) { Text("Готово") } }
        )
    }
}

@Composable
private fun SettingsPill(
    text: String, x: Float, y: Float, width: Float, height: Float,
    scale: Float, onClick: () -> Unit
) {
    Box(
        Modifier.frameAt(x, y, width, height, scale)
            .clip(RoundedCornerShape((65.29f * scale).dp))
            .background(Color(0xFF242424))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = Color.White, fontFamily = MiSansFamily,
            fontWeight = FontWeight.Normal, fontSize = (53f * scale).sp,
            textAlign = TextAlign.Center, maxLines = 1
        )
    }
}

@Composable
private fun SelectedIcon(pkg: String) {
    val context = LocalContext.current
    val bitmap = remember(pkg) {
        runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(32.dp))
    } else {
        Box(Modifier.size(32.dp).background(Color(0xFF242424), RoundedCornerShape(7.dp)))
    }
}
