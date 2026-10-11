package tj.umed.drivedashboard

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

private val settingsBg = Color.Black
private val settingsChip = Color(0xFF242424)
private val settingsBorder = Color(0xFF242424)
private data class LaunchApp(val pkg: String, val name: String)

private fun installedLaunchableApps(context: Context): List<LaunchApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val matches = pm.queryIntentActivities(intent, 0)
    return matches.map { LaunchApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filterNot { it.pkg == context.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.name.lowercase() }
}

/** The settings canvas exactly follows the 1080 x 2961 Figma frame.
 * Design coordinates stay constant, while Android scales the entire frame to device width.
 * Vertical scrolling permits access to all six gears without compressing the layout.
 */
@Composable
fun SettingsScreen(
    controller: DashboardController,
    onBack: () -> Unit,
    availableRefreshRates: List<Int>,
    onRefreshRateChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val allApps = remember { installedLaunchableApps(context) }
    var appPicker by remember { mutableStateOf(false) }
    var appOrder by remember { mutableStateOf(false) }
    var playerPicker by remember { mutableStateOf(false) }
    var creditsDialog by remember { mutableStateOf(false) }
    var refreshMenuExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(settingsBg)) {
        val scale = maxWidth.value / 1080f
        fun d(px: Float): Dp = (px * scale).dp
        fun f(px: Float): TextUnit = (px * scale).sp

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.width(d(1080f)).height(d(2961f))) {
                // Settings heading and Figma back arrow.
                Image(
                    painter = painterResource(R.drawable.figma_back_arrow),
                    contentDescription = "Back",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.offset(x=d(41f), y=d(157.2f))
                        .size(d(72.276f),d(62.969f)).clickable(onClick=onBack)
                )
                FigmaSettingsText(
                    "Settings", f(110.835f), FontWeight.Light,
                    modifier = Modifier.offset(y = d(115.676f)).fillMaxWidth().height(d(147f)),
                    align = TextAlign.Center
                )
                FigmaSettingsText("Applications", f(69.782f), FontWeight.Normal,
                    Modifier.offset(x=d(41f),y=d(339.644f)).size(d(430f),d(93f)))
                FigmaSettingsPill("Select", d(576f),d(338.676f),d(220.629f),d(93.487f),f(47.931f),{appPicker=true})
                FigmaSettingsPill("Order",d(818f),d(338.676f),d(220.629f),d(93.487f),f(47.931f),{appOrder=true})
                FigmaRule(d(41f),d(495.676f),d(998f),d(3f))
                FigmaSettingsText("Music player", f(69.782f), FontWeight.Normal,
                    Modifier.offset(x=d(41f),y=d(560.61f)).size(d(510f),d(93f)))
                FigmaSettingsPill("Select",d(818f),d(559.676f),d(220.629f),d(93.487f),f(47.931f),{playerPicker=true})
                FigmaRule(d(41f),d(716.676f),d(998f),d(3f))
                FigmaSettingsText("Gearbox",f(69.782f),FontWeight.Normal,
                    Modifier.offset(y=d(780.676f)).fillMaxWidth().height(d(93f)),TextAlign.Center)

                for (row in 0..2) {
                    for (column in 0..1) {
                        val input = controller.gearInputs[row*2+column]
                        val error = controller.gearErrors[input.number]
                        FigmaGearSettingCard(
                            input = input,
                            error = error,
                            scale = scale,
                            onEdit = { field,value -> controller.setGearField(input.number, field, value) },
                            modifier = Modifier.offset(
                                x=d(if(column==0) 41f else 578f),
                                y=d(909.676f+row*624f)
                            ).size(d(461f),d(545f))
                        )
                    }
                }
            }
            // Additional V1 feature below the original Figma settings frame,
            // so none of the six gear cards or Figma coordinates are shifted.
            Column(
                Modifier.width(d(998f)).padding(top = d(36f), bottom = d(20f)),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "Display refresh rate",
                    color = Color.White,
                    fontFamily = MiSansFamily,
                    fontSize = f(50f),
                    fontWeight = FontWeight.Normal
                )
                Spacer(Modifier.height(d(22f)))
                Box {
                    Row(
                        Modifier.clip(CircleShape).background(settingsChip)
                            .clickable { refreshMenuExpanded = true }
                            .padding(horizontal = d(44f), vertical = d(22f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (controller.refreshRateHz == 0) "Auto"
                            else "${controller.refreshRateHz} Hz",
                            color = Color.White,
                            fontFamily = MiSansFamily,
                            fontSize = f(43f)
                        )
                        Spacer(Modifier.width(d(20f)))
                        Text("▾", color = Color.LightGray, fontSize = f(38f))
                    }
                    DropdownMenu(
                        expanded = refreshMenuExpanded,
                        onDismissRequest = { refreshMenuExpanded = false }
                    ) {
                        val choices = (listOf(0, 10, 30) + availableRefreshRates).distinct().sorted()
                        choices.forEach { hz ->
                            val supported = hz == 0 || hz in availableRefreshRates
                            DropdownMenuItem(
                                enabled = supported,
                                text = {
                                    Text(
                                        when {
                                            hz == 0 -> "Auto (system)"
                                            supported -> "${hz} Hz"
                                            else -> "${hz} Hz — unsupported"
                                        },
                                        fontFamily = MiSansFamily
                                    )
                                },
                                onClick = {
                                    onRefreshRateChange(hz)
                                    refreshMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(d(22f)))
                Text(
                    "Available display modes: " +
                        (if (availableRefreshRates.isEmpty()) "system controlled"
                         else availableRefreshRates.joinToString(", ") { "$it Hz" }) +
                        ". Android/HyperOS may override the requested rate. " +
                        "10 or 30 Hz cannot be forced unless supported by the panel.",
                    fontFamily = MiSansFamily,
                    fontSize = f(27f),
                    color = Color(0xFF9A9A9A),
                    lineHeight = f(39f)
                )
            }
            // Credit is outside the original frame, preserving its pixel geometry.
            Text(
                "MiSans Latin font by Xiaomi · Font credits",
                modifier = Modifier.clickable { creditsDialog=true }.padding(vertical = 12.dp),
                color = Color(0xFF777777),fontSize=10.sp
            )
        }
    }
    if(appPicker) AppSelectDialog(allApps,controller.pinnedApps,onToggle={pkg->
        val old=controller.pinnedApps
        if(pkg in old) controller.setPinned(old-pkg)
        else if(old.size<8) controller.setPinned(old+pkg)
    },onDismiss={appPicker=false})
    if(appOrder) AppOrderDialog(allApps,controller.pinnedApps,controller::setPinned,{appOrder=false})
    if(playerPicker) PlayerSelectDialog(allApps,controller.playerPackage,
        onChoose={controller.selectPlayerPackage(it);playerPicker=false},onDismiss={playerPicker=false})
    if(creditsDialog) AlertDialog(onDismissRequest={creditsDialog=false},
        title={Text("Font credits")},
        text={Text("MiSans Latin font by Xiaomi. Fonts embedded for display in DriveDashboard; see the MiSans fonts intellectual property license agreement.")},
        confirmButton={TextButton(onClick={creditsDialog=false}){Text("OK")}})
}

@Composable
private fun FigmaSettingsText(
    label:String,fontSize:TextUnit,weight:FontWeight,modifier:Modifier=Modifier,align:TextAlign=TextAlign.Start
){
    Box(modifier,contentAlignment= when(align) {
        TextAlign.Center -> Alignment.Center
        TextAlign.End -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }) {
        Text(label,fontSize=fontSize,fontFamily=MiSansFamily,fontWeight=weight,
            color=Color.White,style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)),
            textAlign=align,maxLines=1)
    }
}

@Composable
private fun FigmaSettingsPill(label:String,x:Dp,y:Dp,w:Dp,h:Dp,textSize:TextUnit,action:()->Unit) {
    Box(Modifier.offset(x=x,y=y).size(w,h).clip(CircleShape).background(settingsChip)
        .clickable { action() },contentAlignment=Alignment.Center) {
        Text(label,fontFamily=MiSansFamily,fontWeight=FontWeight.Normal,color=Color.White,fontSize=textSize,
            style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)))
    }
}

@Composable
private fun FigmaRule(x:Dp,y:Dp,w:Dp,h:Dp) {
    Box(Modifier.offset(x=x,y=y).size(w,h).background(Color(0xFF242424)))
}

/** One 461px wide gear card with all four fields on exact Figma grid. */
@Composable
private fun FigmaGearSettingCard(input:GearInput,error:String?,scale:Float,
    onEdit:(Int,String)->Unit,modifier:Modifier=Modifier
) {
    fun d(px:Float):Dp=(px*scale).dp
    fun f(px:Float):TextUnit=(px*scale).sp
    Box(modifier) {
        Box(
            Modifier.offset(x=d(184f),y=d(0f)).size(d(93.49f))
                .background(settingsChip,CircleShape),contentAlignment=Alignment.Center
        ) {
            Text("${input.number}",fontSize=f(47.931f),fontFamily=MiSansFamily,color=Color.White,
                fontWeight=FontWeight.Normal)
        }
        FigmaSettingsText("Speed",f(50f),FontWeight.Normal,
            Modifier.offset(y=d(115f)).fillMaxWidth().height(d(66f)),TextAlign.Center)
        FigmaGearNumberField(input.speedMin,onValue={onEdit(0,it)},hasError=error!=null,scale=scale,
            modifier=Modifier.offset(x=d(0f),y=d(196f)).size(d(223f),d(110f)))
        FigmaGearNumberField(input.speedMax,onValue={onEdit(1,it)},hasError=error!=null,scale=scale,
            modifier=Modifier.offset(x=d(238f),y=d(196f)).size(d(223f),d(110f)))
        FigmaSettingsText("RPM",f(50f),FontWeight.Normal,
            Modifier.offset(y=d(345f)).fillMaxWidth().height(d(66f)),TextAlign.Center)
        FigmaGearNumberField(input.rpmMin,onValue={onEdit(2,it)},hasError=error!=null,scale=scale,
            modifier=Modifier.offset(x=d(0f),y=d(426f)).size(d(223f),d(110f)))
        FigmaGearNumberField(input.rpmMax,onValue={onEdit(3,it)},hasError=error!=null,scale=scale,
            modifier=Modifier.offset(x=d(238f),y=d(426f)).size(d(223f),d(110f)))
        if(error!=null) Text(error,Modifier.offset(y=d(544f)).fillMaxWidth(),
            color=Color(0xFFFF7777),fontSize=f(25f),textAlign=TextAlign.Center,maxLines=1)
    }
}

@Composable
private fun FigmaGearNumberField(value:String,onValue:(String)->Unit,hasError:Boolean,
    scale:Float,modifier:Modifier=Modifier
) {
    Box(modifier.border((5f*scale).dp,
        if(hasError) Color(0xFFD65454) else settingsBorder,CircleShape),contentAlignment=Alignment.Center) {
        BasicTextField(
            value=value,onValueChange=onValue,singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
            cursorBrush=SolidColor(Color.White),
            textStyle=TextStyle(color=Color.White,fontFamily=MiSansFamily,fontWeight=FontWeight.Normal,
                fontSize=(50f*scale).sp,textAlign=TextAlign.Center,
                platformStyle=PlatformTextStyle(includeFontPadding=false)),
            modifier=Modifier.fillMaxWidth().padding(horizontal=(10f*scale).dp)
        )
    }
}

@Composable
private fun AppIcon(pkg:String,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val bitmap=remember(pkg){runCatching {context.packageManager.getApplicationIcon(pkg).toBitmap(80,80).asImageBitmap()}.getOrNull()}
    if(bitmap!=null) Image(bitmap, pkg, modifier.size(32.dp))
    else Box(modifier.size(32.dp).background(settingsChip,RoundedCornerShape(7.dp)))
}

@Composable
private fun AppSelectDialog(apps:List<LaunchApp>,selected:List<String>,onToggle:(String)->Unit,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Applications (${selected.size}/8)")},
        text={
            LazyColumn(Modifier.heightIn(max=440.dp)) {
                items(apps,key={it.pkg}) { app ->
                    Row(Modifier.fillMaxWidth().clickable {onToggle(app.pkg)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        AppIcon(app.pkg)
                        Spacer(Modifier.width(10.dp))
                        Text(app.name,Modifier.weight(1f),maxLines=1,fontSize=14.sp)
                        Checkbox(checked=app.pkg in selected,onCheckedChange={onToggle(app.pkg)},enabled=app.pkg in selected || selected.size<8)
                    }
                }
            }
        },confirmButton={TextButton(onClick=onDismiss){Text("Готово")}})
}

@Composable
private fun AppOrderDialog(apps:List<LaunchApp>,selected:List<String>,onReorder:(List<String>)->Unit,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Порядок приложений")},
        text={Column { if(selected.isEmpty()) Text("Сначала выбери приложения",fontSize=13.sp)
            selected.forEachIndexed { index,pkg ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    AppIcon(pkg)
                    Spacer(Modifier.width(7.dp))
                    Text(apps.firstOrNull{it.pkg==pkg}?.name ?: pkg,Modifier.weight(1f),fontSize=13.sp,maxLines=1)
                    IconButton(onClick={if(index>0) { val l=selected.toMutableList(); java.util.Collections.swap(l,index,index-1);onReorder(l) }else { }},enabled=index>0) {
                        Icon(Icons.Default.ArrowUpward,"Выше")
                    }
                    IconButton(onClick={if(index<selected.lastIndex) {val l=selected.toMutableList();java.util.Collections.swap(l,index,index+1);onReorder(l)}else { }},enabled=index<selected.lastIndex) {
                        Icon(Icons.Default.ArrowDownward,"Ниже")
                    }
                }
            }
        }},confirmButton={TextButton(onClick=onDismiss){Text("Готово")}})
}

@Composable
private fun PlayerSelectDialog(apps:List<LaunchApp>,selected:String,onChoose:(String)->Unit,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Music player")},
        text={LazyColumn(Modifier.heightIn(max=450.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clickable {onChoose("")}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Автоматически (активный плеер)",Modifier.weight(1f),fontSize=14.sp)
                    if(selected.isBlank()) Icon(Icons.Default.Check,"Выбрано")
                }
            }
            items(apps,key={it.pkg}){app->
                Row(Modifier.fillMaxWidth().clickable {onChoose(app.pkg)}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                    AppIcon(app.pkg);Spacer(Modifier.width(10.dp))
                    Text(app.name,Modifier.weight(1f),fontSize=14.sp,maxLines=1)
                    if(app.pkg==selected) Icon(Icons.Default.Check,"Выбрано")
                }
            }
        }},confirmButton={TextButton(onClick=onDismiss){Text("Закрыть")}})
}