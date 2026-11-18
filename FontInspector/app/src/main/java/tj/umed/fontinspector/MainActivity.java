package tj.umed.fontinspector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MainActivity extends Activity {
    private static final int BG=0xFF101114, SURFACE=0xFF1D1F23, SURFACE2=0xFF292B30,
        WHITE=0xFFF3F3F4, MUTED=0xFFA0A4AC, ACCENT=0xFFD0D3DA, GREEN=0xFFB6DBC4;
    private static final int SAVE_JSON=81;
    private static final String[] DIRS={"/system/fonts","/product/fonts","/system_ext/fonts",
        "/vendor/fonts","/system/framework/fonts","/data/fonts","/data/system/theme/fonts"};
    private static final String[] CONFIGS={"/system/etc/fonts.xml","/system/etc/font_fallback.xml",
        "/system/etc/system_fonts.xml","/product/etc/fonts_customization.xml",
        "/system/etc/fonts_customization.xml","/system_ext/etc/fonts.xml",
        "/vendor/etc/fonts.xml","/data/system/theme/fonts.xml"};
    private final Set<String> seen=new HashSet<>();
    private LinearLayout body;
    private volatile boolean busy=false;
    private Result data=new Result();
    private String status="Подготовка", query="";
    private int tab=0;

    static final class FontInfo {
        String path="",family="",subfamily="",fullName="",format="",axis="",origin="";
        int weight=400,faceIndex=0,faceCount=1; boolean italic=false,variable=false;
        JSONObject json() throws Exception {
            JSONObject j=new JSONObject();j.put("path",path);j.put("family",family);
            j.put("subfamily",subfamily);j.put("full_name",fullName);j.put("format",format);
            j.put("weight",weight);j.put("weight_label",weightLabel(weight));j.put("italic",italic);
            j.put("variable",variable);j.put("axes",axis);j.put("face_index",faceIndex);
            j.put("faces_in_collection",faceCount);j.put("source",origin);return j;
        }
    }
    static final class Mapping {
        String config="",family="",weight="",style="",file="",details="";
        JSONObject json() throws Exception {JSONObject j=new JSONObject();
            j.put("config",config);j.put("family",family);j.put("weight",weight);
            j.put("style",style);j.put("file",file);j.put("details",details);return j;}
    }
    static final class Area {
        String name="",description="",packageName="",confidence="",resources="",source="";
        JSONObject json() throws Exception {JSONObject j=new JSONObject();
            j.put("area",name);j.put("description",description);j.put("owner_package",packageName);
            j.put("evidence",source);j.put("confidence",confidence);
            j.put("packaged_font_resources",resources);return j;}
    }
    static final class Result {
        List<FontInfo> fonts=new ArrayList<>();
        List<Mapping> mappings=new ArrayList<>();
        List<Area> areas=new ArrayList<>();
        List<String> warnings=new ArrayList<>(),configs=new ArrayList<>();
        boolean root=false;
        String scanAt="";
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        render();scan(false);
    }
    private int dp(float a){return (int)(getResources().getDisplayMetrics().density*a+0.5f);}
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    private TextView text(String value,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setIncludeFontPadding(true);return t;
    }
    private LinearLayout vert(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout horiz(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void add(LinearLayout parent,View v,int marginBottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(marginBottom);
        parent.addView(v,p);
    }
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private TextView chip(String title){TextView t=text(title,11,MUTED,true);t.setLetterSpacing(0.08f);return t;}
    private Button button(String title,boolean primary){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(12);
        b.setTextColor(primary?BG:WHITE);b.setBackground(shape(primary?WHITE:SURFACE2,13));
        b.setMinHeight(dp(43));b.setPadding(dp(9),0,dp(9),0);return b;
    }
    private LinearLayout card(){
        LinearLayout c=vert();c.setBackground(shape(SURFACE,18));pad(c,16,15);return c;
    }
    private void heading(LinearLayout parent,String title,String subtitle){
        add(parent,text(title,19,WHITE,true),4);add(parent,text(subtitle,12,MUTED,false),15);
    }
    private void render(){
        LinearLayout outer=vert();outer.setBackgroundColor(BG);
        setContentView(outer);
        LinearLayout head=vert();pad(head,18,17);
        add(head,chip("SYSTEM TYPOGRAPHY  /  DIAGNOSTICS"),8);
        add(head,text("Font Inspector",26,WHITE,true),4);
        add(head,text("MIUI · Android 13+ · только чтение",12,MUTED,false),13);
        add(head,text(status,12,busy?ACCENT:GREEN,false),6);
        outer.addView(head,new LinearLayout.LayoutParams(-1,-2));

        HorizontalScrollView nav=new HorizontalScrollView(this);
        nav.setHorizontalScrollBarEnabled(false);
        LinearLayout navRow=horiz();navRow.setPadding(dp(14),0,dp(14),dp(9));
        String[] names={"Обзор","Шрифты","Экраны","XML"};
        for(int i=0;i<4;i++){final int at=i;Button b=button(names[i],tab==i);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(41),1f);
            lp.leftMargin=dp(4);lp.rightMargin=dp(4);navRow.addView(b,lp);
            b.setOnClickListener(v->{tab=at;render();});}
        nav.addView(navRow);outer.addView(nav);

        ScrollView sc=new ScrollView(this);sc.setFillViewport(false);
        body=vert();body.setPadding(dp(18),dp(12),dp(18),dp(24));sc.addView(body);
        outer.addView(sc,new LinearLayout.LayoutParams(-1,0,1f));
        if(tab==0)overview();else if(tab==1)fontsTab();else if(tab==2)areasTab();else xmlTab();

        LinearLayout foot=horiz();foot.setBackgroundColor(BG);foot.setPadding(dp(14),dp(9),dp(14),dp(13));
        Button scan=button(busy?"Сканирование...":"Сканировать",true);
        Button root=button("ROOT-анализ",false),export=button("JSON",false);
        scan.setEnabled(!busy);root.setEnabled(!busy);export.setEnabled(!busy);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(47),1.18f);a.rightMargin=dp(7);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(47),1.1f);b.rightMargin=dp(7);
        foot.addView(scan,a);foot.addView(root,b);foot.addView(export,new LinearLayout.LayoutParams(0,dp(47),0.72f));
        scan.setOnClickListener(v->scan(false));
        root.setOnClickListener(v->scan(true));
        export.setOnClickListener(v->export());
        outer.addView(foot);
    }
    private void overview(){
        heading(body,"Результаты","Реальные файлы, таблицы шрифтов и предполагаемые области использования");
        LinearLayout numbers=horiz();
        mini(numbers,""+data.fonts.size(),"Начертания");
        mini(numbers,""+data.mappings.size(),"XML-записи");
        mini(numbers,""+data.areas.size(),"Экраны");
        add(body,numbers,15);
        LinearLayout c=card();
        add(c,chip("ДОСТУП"),9);
        add(c,text(data.root?"ROOT-проверка выполнена":"Обычное сканирование",16,WHITE,true),7);
        add(c,text(data.root?"Проверены доступные системные каталоги и защищённые пути тем.":"Для защищённых шрифтов MIUI используйте «ROOT-анализ». Доступные системные файлы уже проверяются без ROOT.",13,MUTED,false),0);
        add(body,c,13);

        LinearLayout notes=card();
        add(notes,chip("ВАЖНО О ТОЧНОСТИ"),10);
        add(notes,text("Файл шрифта и его вес определяются по внутренним таблицам TTF/OTF/TTC. XML показывает правила выбора гарнитуры Android.",13,WHITE,false),11);
        add(notes,text("Локскрин, шторка, статус-бар и AOD могут применять шрифт динамически внутри SystemUI. По одним файлам и XML нельзя доказать, какой шрифт установлен у конкретного TextView. Для этого впоследствии потребуется диагностический LSPosed-хук.",13,MUTED,false),0);
        add(body,notes,13);

        if(data.fonts.size()>0){
            LinearLayout sum=card();
            add(sum,chip("АНАЛИЗ НАЧЕРТАНИЙ"),10);
            Map<String,Integer> weights=new java.util.TreeMap<>();
            int variable=0,italic=0;
            for(FontInfo f:data.fonts){String label=weightLabel(f.weight);
                weights.put(label,weights.containsKey(label)?weights.get(label)+1:1);
                if(f.variable)variable++;if(f.italic)italic++;}
            add(sum,text("Курсив: "+italic+"    Вариативные: "+variable,13,WHITE,true),10);
            for(Map.Entry<String,Integer> w:weights.entrySet())add(sum,text(w.getKey()+": "+w.getValue(),12,MUTED,false),6);
            add(body,sum,13);
        }
        if(!data.warnings.isEmpty()){
            LinearLayout warn=card();add(warn,chip("ПРОБЛЕМЫ ДОСТУПА / ЧТЕНИЯ"),10);
            for(int i=0;i<Math.min(10,data.warnings.size());i++)add(warn,text("• "+data.warnings.get(i),12,MUTED,false),7);
            add(body,warn,12);
        }
        add(body,text("Сканирование не изменяет шрифты, темы, SystemUI и разделы устройства. Отчёт JSON сохраняется в папку, выбранную пользователем.",12,MUTED,false),0);
    }
    private void mini(LinearLayout parent,String number,String caption){
        LinearLayout box=vert();box.setBackground(shape(SURFACE,14));pad(box,11,13);
        add(box,text(number,23,WHITE,true),5);add(box,text(caption,11,MUTED,false),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=dp(6);parent.addView(box,lp);
    }
    private void fontsTab(){
        heading(body,"Файлы шрифтов","Семейство, стиль, толщина, формат, путь; нажмите для просмотра");
        LinearLayout filter=horiz();EditText e=new EditText(this);
        e.setSingleLine(true);e.setTextSize(13);e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);e.setHint("Имя, путь, вес...");e.setText(query);
        e.setBackground(shape(SURFACE,12));pad(e,12,10);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(46),1);ep.rightMargin=dp(8);
        filter.addView(e,ep);Button search=button("Найти",true);
        filter.addView(search,new LinearLayout.LayoutParams(dp(82),dp(46)));
        search.setOnClickListener(v->{query=e.getText().toString().trim().toLowerCase(Locale.ROOT);render();});
        add(body,filter,15);
        List<FontInfo> list=new ArrayList<>();
        for(FontInfo f:data.fonts)if((f.family+" "+f.subfamily+" "+f.path+" "+f.axis+" "+weightLabel(f.weight)).toLowerCase(Locale.ROOT).contains(query))list.add(f);
        add(body,text("Найдено: "+list.size(),12,MUTED,false),11);
        int show=Math.min(list.size(),250);
        for(int i=0;i<show;i++){
            final FontInfo f=list.get(i);LinearLayout c=card();
            add(c,text(f.family,16,WHITE,true),4);
            String style=(f.subfamily.length()>0?f.subfamily:"Regular")+
                "  ·  "+weightLabel(f.weight)+" ("+f.weight+")"+
                (f.italic?"  ·  italic":"")+(f.variable?"  ·  variable":"");
            add(c,text(style,12,ACCENT,false),7);
            add(c,text(f.format+(f.faceCount>1?"  ·  TTC "+(f.faceIndex+1)+"/"+f.faceCount:"")+
                (f.axis.isEmpty()?"":"  ·  "+f.axis),12,MUTED,false),6);
            add(c,text(f.path,11,MUTED,false),0);
            c.setOnClickListener(v->fontDialog(f));add(body,c,9);
        }
        if(list.size()>show)add(body,text("Показаны первые "+show+" записей. В JSON попадут все "+list.size()+". Уточните поиск.",12,MUTED,false),0);
    }
    private void fontDialog(FontInfo f){
        LinearLayout c=vert();pad(c,19,15);
        add(c,text(f.family,22,WHITE,true),10);
        add(c,text("Aa Bb 1234567890\nАа Бб Вв Ёё\nFont Inspector",22,WHITE,false),13);
        TextView preview=(TextView)c.getChildAt(1);
        if(new File(f.path).canRead())try{preview.setTypeface(Typeface.createFromFile(f.path));}
        catch(Exception ignored){}
        add(c,text("Формат: "+f.format+"\nНачертание: "+f.subfamily+
            "\nВес: "+f.weight+" / "+weightLabel(f.weight)+"\nКурсив: "+f.italic+
            "\nВариативный: "+f.variable+"\nОси: "+f.axis+
            "\nФайл: "+f.path+"\nИсточник: "+f.origin,12,MUTED,false),0);
        new AlertDialog.Builder(this).setView(c).setPositiveButton("Закрыть",null).show();
    }
    private void areasTab(){
        heading(body,"Карта интерфейса","Пакеты-владельцы и найденные font-ресурсы APK. Не подмена доказанной типографики");
        for(Area a:data.areas){
            LinearLayout c=card();
            add(c,text(a.name,16,WHITE,true),5);
            add(c,text(a.description,12,MUTED,false),9);
            add(c,text(a.packageName.isEmpty()?"Пакет не обнаружен":"Пакет: "+a.packageName,12,ACCENT,true),6);
            add(c,text(a.confidence,12,MUTED,false),6);
            if(!a.resources.isEmpty())add(c,text("font-ресурсы APK: "+a.resources,11,MUTED,false),0);
            add(body,c,10);
        }
        LinearLayout note=card();
        add(note,chip("ЗАДАЧА ВТОРОГО ПРИЛОЖЕНИЯ"),8);
        add(note,text("Следующий этап: LSPosed-модуль с перехватом установки Typeface и привязкой к пакету, классу View и идентификатору ресурса. Только после живого анализа можно безопаснее выбирать точку замены.",13,MUTED,false),0);
        add(body,note,12);
    }
    private void xmlTab(){
        heading(body,"Конфигурация Android","family / weight / style / font, найденные в системных XML");
        add(body,text("Прочитаны: "+data.configs.size()+" файлов. Правила fallback не означают, что UI действительно использует каждую запись.",12,MUTED,false),12);
        for(String config:data.configs)add(body,text("• "+config,11,ACCENT,false),7);
        int display=Math.min(220,data.mappings.size());
        for(int i=0;i<display;i++){
            Mapping m=data.mappings.get(i);LinearLayout c=card();
            add(c,text(m.family.isEmpty()?"(fallback / без имени)":m.family,14,WHITE,true),5);
            add(c,text(m.weight+(m.style.isEmpty()?"":" · "+m.style),12,ACCENT,false),6);
            add(c,text(m.file,12,MUTED,false),5);
            add(c,text(m.config+(m.details.isEmpty()?"":"  ·  "+m.details),10,MUTED,false),0);
            add(body,c,8);
        }
        if(data.mappings.size()>display)add(body,text("Полный список из "+data.mappings.size()+" записей находится в JSON.",12,MUTED,false),0);
    }

    private void scan(boolean withRoot){
        if(busy)return;
        busy=true;status=withRoot?"Проверка ROOT и анализ защищённых папок...":"Анализ системных шрифтов...";
        render();
        new Thread(()->{
            Result r=new Result();seen.clear();
            r.scanAt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.US).format(new Date());
            if(withRoot){r.root=isRoot();if(!r.root)r.warnings.add("ROOT не предоставлен: выполнен обычный анализ.");}
            for(String path:DIRS)walk(new File(path),0,r);
            if(r.root)scanProtected(r);
            for(String path:CONFIGS){try{
                File f=new File(path);
                if(!f.canRead()&&r.root)f=rootCopy(path,"config");
                if(f!=null&&f.isFile()&&f.canRead())readConfig(f,path,r);
                if(f!=null&&!path.equals(f.getAbsolutePath()))f.delete();
            }catch(Exception ex){r.warnings.add("XML "+path+": "+ex.getClass().getSimpleName());}}
            inspectAreas(r);
            Collections.sort(r.fonts,Comparator.comparing(f->(f.family+" "+f.path).toLowerCase(Locale.ROOT)));
            runOnUiThread(()->{data=r;busy=false;
                status="Готово: "+r.fonts.size()+" начертаний · "+r.mappings.size()+
                    " XML · ROOT "+(r.root?"есть":"нет");
                render();
            });
        },"font-scan").start();
    }
    private void walk(File dir,int depth,Result r){
        if(depth>3||r.fonts.size()>900||!dir.isDirectory())return;
        File[] items=dir.listFiles();if(items==null)return;
        for(File f:items){
            if(f.isDirectory())walk(f,depth+1,r);
            else if(isFont(f.getName()))addFont(f,f.getAbsolutePath(),"filesystem",r);
            if(r.fonts.size()>900)break;
        }
    }
    private boolean isFont(String s){
        String a=s.toLowerCase(Locale.ROOT);
        return a.endsWith(".ttf")||a.endsWith(".otf")||a.endsWith(".ttc")||
            a.endsWith(".otc")||a.endsWith(".woff")||a.endsWith(".woff2");
    }
    private void addFont(File file,String original,String source,Result r){
        if(!seen.add(original))return;
        if(!file.isFile()||!file.canRead()){r.warnings.add("Недоступен шрифт: "+original);return;}
        try{r.fonts.addAll(FontParser.read(file,original,source));}
        catch(Exception ex){r.warnings.add("Ошибка разбора "+original+": "+ex.getMessage());}
    }
    private boolean isRoot(){
        try{Process p=new ProcessBuilder("su","-c","id -u").redirectErrorStream(true).start();
            if(!p.waitFor(7,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();return false;}
            ByteArrayOutputStream out=new ByteArrayOutputStream();copyBounded(p.getInputStream(),out,128);
            return p.exitValue()==0&&out.toString("UTF-8").trim().equals("0");
        }catch(Exception e){return false;}
    }
    private static void copyBounded(InputStream in,OutputStream out,int max)throws Exception{
        byte[] buf=new byte[8192];int n,total=0;
        while((n=in.read(buf))!=-1){int size=Math.min(n,max-total);
            if(size>0)out.write(buf,0,size);total+=size;if(total>=max)break;}
    }
    private String rootCommand(String command,int limit)throws Exception{
        Process p=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try{copyBounded(p.getInputStream(),out,limit);}
            catch(Exception ignored){}});reader.setDaemon(true);reader.start();
        if(!p.waitFor(15,java.util.concurrent.TimeUnit.SECONDS))p.destroyForcibly();
        reader.join(500);return out.toString("UTF-8");
    }
    private String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    private File rootCopy(String path,String label){
        try{
            File tmp=File.createTempFile("fontscan_"+label+"_","_temp",getCacheDir());
            String cmd="cp "+quote(path)+" "+quote(tmp.getAbsolutePath())+
                " && chmod 644 "+quote(tmp.getAbsolutePath());
            rootCommand(cmd,1500);
            if(tmp.length()>0&&tmp.length()<80L*1024*1024)return tmp;
            tmp.delete();
        }catch(Exception ignored){}
        return null;
    }
    private void scanProtected(Result r){
        try{
            String command="find /data/fonts /data/system/theme/fonts /data/system/theme "+
                "-maxdepth 4 -type f 2>/dev/null | grep -Ei '\\.(ttf|otf|ttc|otc|woff2?)$' | head -n 160";
            String output=rootCommand(command,40000);
            for(String path:output.split("\\r?\\n")){
                if(path.isEmpty()||path.length()>400||seen.contains(path)||!isFont(path))continue;
                File f=new File(path),tmp=null;
                if(!f.canRead())tmp=rootCopy(path,"font");
                addFont(tmp!=null?tmp:f,path,tmp!=null?"root copy":"root path",r);
                if(tmp!=null)tmp.delete();
            }
        }catch(Exception ex){r.warnings.add("ROOT-поиск: "+ex.getMessage());}
    }
    private void readConfig(File file,String original,Result r)throws Exception{
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setExpandEntityReferences(false);
        InputStream in=new BufferedInputStream(new FileInputStream(file));
        Document doc;try{doc=factory.newDocumentBuilder().parse(in);}finally{in.close();}
        r.configs.add(original);
        NodeList all=doc.getElementsByTagName("family");
        for(int i=0;i<all.getLength();i++){
            Element family=(Element)all.item(i);String name=family.getAttribute("name");
            String desc=family.getAttribute("lang");
            NodeList children=family.getElementsByTagName("font");
            for(int k=0;k<children.getLength();k++){
                Element font=(Element)children.item(k);
                Mapping m=new Mapping();m.config=original;m.family=name;
                m.style=font.getAttribute("style");m.weight=font.getAttribute("weight");
                m.file=font.getTextContent().trim();m.details=desc;
                if(r.mappings.size()<2500)r.mappings.add(m);
            }
        }
        NodeList aliases=doc.getElementsByTagName("alias");
        for(int i=0;i<aliases.getLength();i++){
            Element a=(Element)aliases.item(i);
            Mapping m=new Mapping();m.config=original;m.family=a.getAttribute("name");
            m.weight=a.getAttribute("weight");m.style="alias";
            m.file="→ "+a.getAttribute("to");m.details="Android family alias";
            if(r.mappings.size()<2500)r.mappings.add(m);
        }
    }
    private void inspectAreas(Result r){
        String[][] areas={
            {"Статус-бар","Часы, подписи и системные индикаторы","com.android.systemui"},
            {"Шторка и быстрые настройки","Quick Settings, плитки, яркость, подписи","com.android.systemui"},
            {"Уведомления","Заголовки и текст системной панели","com.android.systemui"},
            {"Экран блокировки","Часы, дата, уведомления, надписи","com.android.systemui","com.android.keyguard"},
            {"Always-on Display","AOD, часы, подписи","com.miui.aod","com.android.systemui"},
            {"Рабочий стол","Подписи ярлыков, папки и виджеты лаунчера","com.miui.home","com.android.launcher3"},
            {"Недавние приложения","Подписи карточек / элементы Recents","com.miui.home","com.android.systemui"},
            {"Настройки","Списки и названия системных параметров","com.android.settings"},
            {"Безопасность MIUI","Экран системного менеджера","com.miui.securitycenter"},
            {"Экран ввода","Клавиатура, подсказки и символы","com.google.android.inputmethod.latin","com.sohu.inputmethod.sogou.xiaomi"},
            {"Меню выключения","Системное меню питания","com.android.systemui"}
        };
        PackageManager pm=getPackageManager();
        Map<String,String> cache=new HashMap<>();
        for(String[] spec:areas){
            Area area=new Area();area.name=spec[0];area.description=spec[1];
            for(int i=2;i<spec.length;i++){
                try{
                    ApplicationInfo info=pm.getApplicationInfo(spec[i],0);
                    area.packageName=spec[i];
                    area.source="Установленный пакет: "+info.sourceDir;
                    if(!cache.containsKey(spec[i]))cache.put(spec[i],getPackagedFonts(info.sourceDir));
                    area.resources=cache.get(spec[i]);break;
                }catch(Exception ignored){}
            }
            area.confidence=area.packageName.isEmpty()?
                "Владелец не подтверждён; MIUI может использовать другой пакет.":
                "Область вероятно принадлежит пакету. Конкретный Typeface, его вес и имя файла НЕ установлены.";
            r.areas.add(area);
        }
    }
    private String getPackagedFonts(String apk){
        if(apk==null||!new File(apk).canRead())return "APK недоступен для статического чтения";
        StringBuilder s=new StringBuilder();int count=0;
        try(ZipFile zip=new ZipFile(apk)){
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()&&count<30){
                String name=entries.nextElement().getName();
                if(isFont(name)){if(s.length()>0)s.append(", ");s.append(name);count++;}
            }
        }catch(Exception e){return "APK недоступен: "+e.getClass().getSimpleName();}
        return count==0?"В APK нет отдельных font-файлов":s.toString();
    }
    static String weightLabel(int w){
        if(w<=149)return "Thin";if(w<=249)return "ExtraLight";
        if(w<=349)return "Light";if(w<=449)return "Regular";
        if(w<=549)return "Medium";if(w<=649)return "SemiBold";
        if(w<=749)return "Bold";if(w<=849)return "ExtraBold";return "Black";
    }
    private void export(){
        if(data.fonts.isEmpty()&&data.areas.isEmpty()){
            new AlertDialog.Builder(this).setMessage("Сначала выполните сканирование.")
                .setPositiveButton("OK",null).show();return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"FontInspector_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date())+".json");
        startActivityForResult(intent,SAVE_JSON);
    }
    @Override protected void onActivityResult(int request,int code,Intent intent){
        super.onActivityResult(request,code,intent);
        if(request!=SAVE_JSON||code!=RESULT_OK||intent==null||intent.getData()==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(intent.getData())){
            if(out==null)throw new Exception("Нет выходного потока");
            out.write(report().toString(2).getBytes(StandardCharsets.UTF_8));
            out.flush();new AlertDialog.Builder(this).setMessage("Отчёт JSON сохранён.")
                .setPositiveButton("OK",null).show();
        }catch(Exception e){new AlertDialog.Builder(this).setMessage("Ошибка сохранения: "+e)
            .setPositiveButton("OK",null).show();}
    }
    private JSONObject report()throws Exception{
        JSONObject j=new JSONObject();j.put("app","Font Inspector");j.put("schema",1);
        j.put("scanned_at",data.scanAt);j.put("manufacturer",Build.MANUFACTURER);
        j.put("model",Build.MODEL);j.put("android",Build.VERSION.RELEASE);
        j.put("sdk",Build.VERSION.SDK_INT);j.put("rom",Build.DISPLAY);
        j.put("root_granted",data.root);
        JSONArray fonts=new JSONArray();for(FontInfo f:data.fonts)fonts.put(f.json());j.put("fonts",fonts);
        JSONArray mappings=new JSONArray();for(Mapping m:data.mappings)mappings.put(m.json());j.put("font_configuration",mappings);
        JSONArray areas=new JSONArray();for(Area a:data.areas)areas.put(a.json());j.put("interface_areas",areas);
        JSONArray warnings=new JSONArray();for(String w:data.warnings)warnings.put(w);j.put("warnings",warnings);
        JSONArray xml=new JSONArray();for(String p:data.configs)xml.put(p);j.put("config_files",xml);
        j.put("accuracy_notice","Typeface used by an individual UI view cannot be determined by static file enumeration; runtime LSPosed instrumentation is needed.");
        return j;
    }
    static final class FontParser {
        private static int tag(String s){
            byte[] b=s.getBytes(StandardCharsets.US_ASCII);return ((b[0]&255)<<24)|((b[1]&255)<<16)|((b[2]&255)<<8)|(b[3]&255);
        }
        private static long uint(RandomAccessFile raf)throws Exception{return raf.readInt()&0xffffffffL;}
        private static final class Table {long off,len;Table(long a,long b){off=a;len=b;}}
        static List<FontInfo> read(File file,String path,String source)throws Exception{
            List<FontInfo> list=new ArrayList<>();
            String low=path.toLowerCase(Locale.ROOT),fmt=
                low.endsWith(".ttc")?"TTC":low.endsWith(".otc")?"OTC":
                low.endsWith(".otf")?"OTF":low.endsWith(".woff2")?"WOFF2":
                low.endsWith(".woff")?"WOFF":"TTF";
            if(fmt.startsWith("WOFF")){FontInfo fi=base(path,source,fmt);list.add(fi);return list;}
            try(RandomAccessFile raf=new RandomAccessFile(file,"r")){
                int signature=raf.readInt();
                if(signature==tag("ttcf")){
                    raf.seek(8);long n=uint(raf);if(n<1||n>64)throw new Exception("Invalid TTC count");
                    long[] offsets=new long[(int)n];
                    for(int i=0;i<offsets.length;i++)offsets[i]=uint(raf);
                    for(int i=0;i<offsets.length;i++)list.add(parseFace(raf,offsets[i],path,source,fmt,i,offsets.length));
                }else list.add(parseFace(raf,0,path,source,fmt,0,1));
            }
            return list;
        }
        private static FontInfo base(String path,String origin,String format){
            FontInfo f=new FontInfo();f.path=path;f.format=format;f.origin=origin;
            String name=new File(path).getName().replaceAll("(?i)\\.(ttf|otf|ttc|otc|woff2?)$","");
            f.family=name;f.subfamily="не указано";return f;
        }
        private static FontInfo parseFace(RandomAccessFile raf,long base,String path,String origin,
                                          String fmt,int index,int total)throws Exception{
            FontInfo f=base(path,origin,fmt);f.faceIndex=index;f.faceCount=total;
            if(base<0||base+12>raf.length())return f;
            raf.seek(base);int magic=raf.readInt();
            if(magic!=0x00010000&&magic!=tag("OTTO")&&magic!=tag("true"))return f;
            int tables=raf.readUnsignedShort();if(tables<1||tables>200)return f;
            Map<Integer,Table> t=new HashMap<>();
            raf.seek(base+12);
            for(int i=0;i<tables;i++){
                int type=raf.readInt();raf.readInt();long offset=uint(raf),size=uint(raf);
                if(offset>=0&&offset+size<=raf.length())t.put(type,new Table(offset,size));
            }
            Table os=t.get(tag("OS/2"));if(os!=null&&os.len>=8){
                raf.seek(os.off+4);f.weight=raf.readUnsignedShort();
                if(os.len>=64){raf.seek(os.off+62);f.italic=(raf.readUnsignedShort()&1)!=0;}
            }
            Table head=t.get(tag("head"));if(head!=null&&head.len>=46){
                raf.seek(head.off+44);f.italic|=(raf.readUnsignedShort()&2)!=0;
            }
            Table name=t.get(tag("name"));
            if(name!=null&&name.len>6&&name.len<2000000){
                Map<Integer,String> names=names(raf,name);
                String family=names.containsKey(16)?names.get(16):names.get(1);
                String sub=names.containsKey(17)?names.get(17):names.get(2);
                if(family!=null&&!family.isEmpty())f.family=family;
                if(sub!=null&&!sub.isEmpty())f.subfamily=sub;
                if(names.containsKey(4))f.fullName=names.get(4);
                if(sub!=null&&sub.toLowerCase(Locale.ROOT).matches(".*(italic|oblique|курсив).*"))f.italic=true;
            }
            Table variable=t.get(tag("fvar"));if(variable!=null&&variable.len>=16){
                f.variable=true;raf.seek(variable.off+4);
                int axisOffset=raf.readUnsignedShort();raf.readUnsignedShort();
                int axes=raf.readUnsignedShort(),axisSize=raf.readUnsignedShort();
                StringBuilder sb=new StringBuilder();
                for(int i=0;i<Math.min(axes,16);i++){
                    long point=variable.off+axisOffset+(long)i*axisSize;
                    if(axisSize<20||point+20>raf.length())break;
                    raf.seek(point);
                    byte[] key=new byte[4];raf.readFully(key);String axis=new String(key,StandardCharsets.US_ASCII);
                    float min=raf.readInt()/65536f;raf.readInt();float max=raf.readInt()/65536f;
                    if(sb.length()>0)sb.append(", ");
                    sb.append(axis).append(" ").append((int)min).append("–").append((int)max);
                }
                f.axis=sb.toString();
            }
            return f;
        }
        private static Map<Integer,String> names(RandomAccessFile raf,Table t)throws Exception{
            Map<Integer,String> map=new HashMap<>();Map<Integer,Integer> scores=new HashMap<>();
            raf.seek(t.off);raf.readUnsignedShort();
            int count=raf.readUnsignedShort(),storage=raf.readUnsignedShort();
            if(count>2000)return map;
            for(int i=0;i<count;i++){
                raf.seek(t.off+6L+12L*i);
                int platform=raf.readUnsignedShort();raf.readUnsignedShort();
                int lang=raf.readUnsignedShort(),id=raf.readUnsignedShort();
                int len=raf.readUnsignedShort(),offset=raf.readUnsignedShort();
                if(id!=1&&id!=2&&id!=4&&id!=16&&id!=17)continue;
                long at=t.off+storage+offset;
                if(len<2||len>4000||at<0||at+len>raf.length()||at+len>t.off+t.len)continue;
                int score=(platform==3?3:platform==0?2:1)+((lang==0x409||lang==0)?3:0);
                if(scores.containsKey(id)&&scores.get(id)>=score)continue;
                raf.seek(at);byte[] value=new byte[len];raf.readFully(value);
                String str=new String(value,(platform==0||platform==3)?
                    StandardCharsets.UTF_16BE:StandardCharsets.ISO_8859_1).replace("\u0000","").trim();
                if(!str.isEmpty()){map.put(id,str);scores.put(id,score);}
            }
            return map;
        }
    }
}
