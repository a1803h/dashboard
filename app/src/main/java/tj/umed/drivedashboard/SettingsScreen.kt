package tj.umed.drivedashboard

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

private val settingsBg = Color.Black
private val settingsChip = Color(0xFF222222)
private val settingsBorder = Color(0xFF343434)

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

@Composable
fun SettingsScreen(controller: DashboardController, onBack: () -> Unit) {
    val context = LocalContext.current
    val allApps = remember { installedLaunchableApps(context) }
    var appPicker by remember { mutableStateOf(false) }
    var appOrder by remember { mutableStateOf(false) }
    var playerPicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(settingsBg).verticalScroll(rememberScrollState())
        .padding(start=14.dp,end=14.dp,top=14.dp,bottom=28.dp)) {
        Box(Modifier.fillMaxWidth().height(88.dp)) {
            IconButton(onClick=onBack,modifier=Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.ArrowBack,"Back", tint=Color.White,modifier=Modifier.size(33.dp))
            }
            Text("Settings",Modifier.align(Alignment.Center),color=Color.White,fontSize=33.sp,fontWeight=FontWeight.Light)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().heightIn(min=61.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Applications",Modifier.weight(1f),color=Color.White,fontSize=22.sp)
            SettingPill("Select") { appPicker = true }
            Spacer(Modifier.width(8.dp))
            SettingPill("Order") { appOrder = true }
        }
        HorizontalDivider(color=settingsBorder,thickness=1.dp,modifier=Modifier.padding(vertical=15.dp))
        Row(Modifier.fillMaxWidth().heightIn(min=59.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Music player",Modifier.weight(1f),color=Color.White,fontSize=22.sp)
            SettingPill("Select") { playerPicker=true }
        }
        HorizontalDivider(color=settingsBorder,thickness=1.dp,modifier=Modifier.padding(vertical=15.dp))
        Text("Gearbox",Modifier.fillMaxWidth().padding(top=8.dp,bottom=14.dp),fontSize=24.sp,color=Color.White,textAlign=TextAlign.Center)
        for (i in 0..2) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                val left=controller.gearInputs[i*2]
                val right=controller.gearInputs[i*2+1]
                GearSettingCard(left,controller.gearErrors[left.number],Modifier.weight(1f)) {field,value->
                    controller.setGearField(left.number,field,value)
                }
                GearSettingCard(right,controller.gearErrors[right.number],Modifier.weight(1f)) {field,value->
                    controller.setGearField(right.number,field,value)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Text("Настройки сохраняются автоматически. Пустая или незавершённая передача не отображается на дашборде.",
            fontSize=12.sp,color=Color.Gray,lineHeight=16.sp)
    }
    if (appPicker) AppSelectDialog(allApps, controller.pinnedApps, onToggle={pkg->
        val old=controller.pinnedApps
        if(pkg in old) controller.setPinned(old - pkg)
        else if(old.size<8) controller.setPinned(old + pkg)
    },onDismiss={appPicker=false})
    if (appOrder) AppOrderDialog(allApps,controller.pinnedApps,onReorder=controller::setPinned,onDismiss={appOrder=false})
    if (playerPicker) PlayerSelectDialog(allApps,controller.playerPackage,
        onChoose={controller.selectPlayerPackage(it);playerPicker=false},onDismiss={playerPicker=false})
}

@Composable
private fun SettingPill(text:String,onClick:()->Unit) {
    Box(Modifier.clip(CircleShape).background(settingsChip).clickable(onClick=onClick)
        .padding(horizontal=16.dp,vertical=9.dp),contentAlignment=Alignment.Center) {
        Text(text,fontSize=15.sp,color=Color.White)
    }
}

@Composable
private fun GearSettingCard(input: GearInput,error:String?,modifier:Modifier=Modifier,onEdit:(Int,String)->Unit) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally) {
        val inactive=input.isBlank() || error!=null
        Box(Modifier.size(31.dp).background(if(error!=null)Color(0xFF842424)else settingsChip,CircleShape),contentAlignment=Alignment.Center) {
            Text("${input.number}",color=if(inactive)Color.LightGray else Color.White,fontSize=16.sp)
        }
        Spacer(Modifier.height(9.dp))
        Text("Speed",fontSize=16.sp,color=Color.White)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            GearNumberField(input.speedMin,Modifier.weight(1f),error!=null){onEdit(0,it)}
            GearNumberField(input.speedMax,Modifier.weight(1f),error!=null){onEdit(1,it)}
        }
        Spacer(Modifier.height(10.dp))
        Text("RPM",fontSize=16.sp,color=Color.White)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            GearNumberField(input.rpmMin,Modifier.weight(1f),error!=null){onEdit(2,it)}
            GearNumberField(input.rpmMax,Modifier.weight(1f),error!=null){onEdit(3,it)}
        }
        if(error!=null) Text(error,Modifier.fillMaxWidth().padding(top=5.dp),fontSize=10.sp,
            lineHeight=12.sp,color=Color(0xFFFF7777),textAlign=TextAlign.Center)
        if(inactive&&error==null) Text("Неактивна",Modifier.padding(top=5.dp),fontSize=10.sp,color=Color.DarkGray)
    }
}

@Composable
private fun GearNumberField(value:String,modifier:Modifier=Modifier,error:Boolean=false,onValue:(String)->Unit) {
    Box(modifier.height(42.dp).border(1.4.dp,if(error)Color(0xFFB84646)else settingsBorder,CircleShape)
        .padding(horizontal=2.dp),contentAlignment=Alignment.Center) {
        BasicTextField(value=value,onValueChange=onValue,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
            textStyle=TextStyle(color=Color.White,fontSize=15.sp,textAlign=TextAlign.Center,fontFamily=MiSansFamily),
            cursorBrush=SolidColor(Color.White),modifier=Modifier.fillMaxWidth())
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
