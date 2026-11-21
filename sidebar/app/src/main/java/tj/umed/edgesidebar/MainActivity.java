package tj.umed.edgesidebar;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private android.content.SharedPreferences prefs;
    private LinearLayout root;
    private int bg = 0xff161719, card = 0xff292b2f, muted=0xffafb4bd, blue=0xff0b91fb;

    public static int dp(Context c, float x){ return Math.round(x*c.getResources().getDisplayMetrics().density); }
    public static GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
    public static final String EXTRA_SETTINGS="tj.umed.edgesidebar.OPEN_SETTINGS";
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("sidebar",MODE_PRIVATE);
        // Launcher icon: open the sidebar directly, do NOT show a floating trigger.
        boolean openSettings=getIntent().getBooleanExtra(EXTRA_SETTINGS,false);
        if(!openSettings && prefs.getBoolean("enabled",false) && Settings.canDrawOverlays(this)){
            startPanel(true);
            finish();
            overridePendingTransition(0,0);
            return;
        }
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(isFinishing())return;
        if(prefs!=null && prefs.getBoolean("enabled",false) && Settings.canDrawOverlays(this))startPanel(false);
        if(root!=null)render();
    }

    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private LinearLayout group() {
        LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);group.setPadding(dp(this,16),dp(this,14),dp(this,16),dp(this,14));group.setBackground(round(card,dp(this,18)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(this,14));root.addView(group,p);return group;
    }
    private void label(LinearLayout c,String title,String description){
        TextView t=text(title,17,Color.WHITE);t.setTypeface(null,1);c.addView(t);
        if(description!=null){TextView x=text(description,12,muted);x.setPadding(0,dp(this,5),0,dp(this,10));c.addView(x);}
    }
    private void switchRow(LinearLayout c,String title,String description,String key,boolean initial){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(this,8),0,dp(this,8));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
        TextView a=text(title,15,Color.WHITE);copy.addView(a);
        if(description!=null){TextView b=text(description,12,muted);copy.addView(b);}
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
        Switch sw=new Switch(this);sw.setChecked(prefs.getBoolean(key,initial));row.addView(sw);
        row.setOnClickListener(v->sw.setChecked(!sw.isChecked()));
        sw.setOnCheckedChangeListener((b,checked)->{
            prefs.edit().putBoolean(key,checked).apply();
            if("enabled".equals(key) && !checked){
                stopService(new Intent(this,SidebarService.class));
            }else if(prefs.getBoolean("enabled",false)){
                if(Settings.canDrawOverlays(this))startPanel(false);else askOverlay();
            }
        });
        c.addView(row);
    }
    private Button action(String title, Runnable cb) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackground(round(0xff414449,dp(this,13)));
        b.setOnClickListener(v->cb.run());return b;
    }
    private void addButton(LinearLayout c,String title,Runnable action){
        Button b=action(title,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(this,48));lp.setMargins(0,dp(this,5),0,dp(this,5));c.addView(b,lp);
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
        root=new LinearLayout(this);root.setPadding(dp(this,18),dp(this,26),dp(this,18),dp(this,28));root.setOrientation(LinearLayout.VERTICAL);scroll.addView(root);setContentView(scroll);
        TextView title=text("EDGE SIDEBAR",25,Color.WHITE);title.setTypeface(null,1);root.addView(title);
        TextView sub=text("Панель громкости · приложения · жесты",13,muted);sub.setPadding(0,dp(this,4),0,dp(this,22));root.addView(sub);
        LinearLayout g=group();label(g,"Работа панели","Нажатие на иконку приложения на домашнем экране открывает сайдбар по центру. Отдельного экранного значка нет.");
        switchRow(g,"Включить панель","Фоновый сервис без экранного значка","enabled",false);
        switchRow(g,"Запуск жестом LSPosed","Свайп назад, затем удержание ~300 мс","gesture",true);
        switchRow(g,"Запасная область жеста","Если LSPosed-хук недоступен: отдельная область на краю, может мешать жесту «Назад»","edge_overlay_fallback",false);
        switchRow(g,"Фиксированная высота","Иначе появляется рядом с точкой начала жеста","fixed",false);
        switchRow(g,"Сторона до первого свайпа назад","При запуске сайдбар использует сторону последнего свайпа «Назад». Здесь выбирается начальная сторона, если свайпов ещё не было.","right",false);
        LinearLayout scaleGroup=group();
        label(scaleGroup,"Масштаб панели","Пропорционально изменяет всю панель, иконки, кнопки, отступы и ползунок громкости.");
        TextView scaleValue=text("",15,Color.WHITE);
        final int scale=prefs.getInt("scale_percent",100);
        scaleValue.setText(scale+"%");
        scaleGroup.addView(scaleValue);
        SeekBar scaleSeek=new SeekBar(this);
        scaleSeek.setMax(100);
        scaleSeek.setProgress(Math.min(100,Math.max(0,scale-60)));
        scaleGroup.addView(scaleSeek);
        scaleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar v,int progress,boolean user){
                int value=60+progress;
                scaleValue.setText(value+"%");
                if(user){
                    prefs.edit().putInt("scale_percent",value).apply();
                    if(prefs.getBoolean("enabled",false) && Settings.canDrawOverlays(MainActivity.this))
                        startPanel(false);
                }
            }
            public void onStartTrackingTouch(SeekBar v){}
            public void onStopTrackingTouch(SeekBar v){}
        });
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
        scaleGroup.addView(text("60%                                         160%",11,muted),sp);
        LinearLayout geometry=group();
        label(geometry,"Дополнительные размеры","Настройки независимы от общего масштаба.");
        addRangeSlider(geometry,"Масштаб панели приложений","Отдельно изменяет всю колонку приложений, её иконки и кнопку настроек.",
            "app_scale_percent",60,160,100,"%");
        addRangeSlider(geometry,"Отступ от края","Расстояние от левого или правого края до сайдбара.",
            "edge_offset_dp",0,100,4," dp");
        LinearLayout visibility=group();
        label(visibility,"Время показа","Отсчёт обновляется при взаимодействии с панелью. При касании за пределами панели она закрывается сразу.");
        addRangeSlider(visibility,"Автоматическое скрытие","0 секунд — не скрывать автоматически; от 1 до 60 секунд — задержка закрытия.",
            "dismiss_after_seconds",0,60,12," с");
        LinearLayout apps=group();label(apps,"Приложения","Закреплённые приложения всегда в списке. Остальные сортируются по последнему открытию без повторов.");
        addButton(apps,"Выбрать закреплённые приложения",this::choosePins);
        TextView tip=text("Удержание иконки в панели закрепляет или открепляет приложение. Список прокручивается.",12,muted);apps.addView(tip);
        LinearLayout rights=group();label(rights,"Разрешения","Нужно предоставить вручную в Android.");
        addButton(rights,"Поверх других окон  "+(Settings.canDrawOverlays(this)?"✓":"—"),this::askOverlay);
        addButton(rights,"История запуска (резерв)  "+(hasUsageAccess()?"✓":"—"),()->{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));});
        long hookedAt=prefs.getLong("hook_active_at",0);
        label(rights,"LSPosed: "+(hookedAt>0?"получены жесты":"ожидание первого жеста"),"Активируйте модуль и отметьте com.miui.home и com.android.systemui; затем перезагрузите устройство.");
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        addButton(rights,"Не беспокоить  "+(nm.isNotificationPolicyAccessGranted()?"✓":"—"),this::askDnd);
        LinearLayout extra=group();label(extra,"Особенности","Три точки открывают первоначальный диалог громкости с тремя ползунками: медиа, звонок и уведомления. Беззвучный режим и DND независимы.");
        TextView disclaimer=text("LSPosed получает жесты и системный порядок недавних задач из MIUI Home/SystemUI, если прошивка поддерживает эти хуки. При отсутствии прав доступен резерв через статистику использования. Системные разрешения на наложение и режим «Не беспокоить» всё ещё нужны.",12,muted);extra.addView(disclaimer);
    }
    private void addRangeSlider(LinearLayout parent,String title,String description,
                                String key,int min,int max,int defaultValue,String suffix){
        LinearLayout wrapper=new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0,dp(this,6),0,dp(this,15));
        LinearLayout heading=new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text(title,15,Color.WHITE);
        heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        int current=Math.min(max,Math.max(min,prefs.getInt(key,defaultValue)));
        TextView number=text(current+suffix,14,muted);
        number.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        heading.addView(number,new LinearLayout.LayoutParams(dp(this,68),-2));
        wrapper.addView(heading);
        TextView info=text(description,12,muted);
        info.setPadding(0,dp(this,5),0,dp(this,7));
        wrapper.addView(info);
        SeekBar seek=new SeekBar(this);
        seek.setMax(max-min);
        seek.setProgress(current-min);
        wrapper.addView(seek,new LinearLayout.LayoutParams(-1,dp(this,40)));
        LinearLayout ends=new LinearLayout(this);
        TextView left=text(min+suffix,11,muted);
        TextView right=text(max+suffix,11,muted);
        right.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        ends.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        ends.addView(right,new LinearLayout.LayoutParams(0,-2,1));
        wrapper.addView(ends);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                int value=min+progress;
                number.setText(value+suffix);
                if(fromUser)prefs.edit().putInt(key,value).apply();
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){
                if(prefs.getBoolean("enabled",false)&&Settings.canDrawOverlays(MainActivity.this))
                    startPanel(false);
            }
        });
        parent.addView(wrapper,new LinearLayout.LayoutParams(-1,-2));
    }
    private void startPanel(){startPanel(false);}
    private void startPanel(boolean open){
        if(!Settings.canDrawOverlays(this))return;
        Intent s=new Intent(this,SidebarService.class);
        s.setAction(open?SidebarService.ACTION_OPEN_PANEL:SidebarService.ACTION_REFRESH_CONFIG);
        try{
            if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);
        }catch(IllegalStateException ignored){}
    }
    private void askOverlay(){Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));startActivity(i);}
    private void askDnd(){startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));}
    private boolean hasUsageAccess(){
        AppOpsManager ops=(AppOpsManager)getSystemService(APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName())==AppOpsManager.MODE_ALLOWED;
    }
    private void choosePins(){
        ArrayList<AppRepository.Item> all=AppRepository.installed(this);
        if(all.size()==0){Toast.makeText(this,"Нет доступных приложений",Toast.LENGTH_SHORT).show();return;}
        String[] labels=new String[all.size()];
        boolean[] selected=new boolean[all.size()];
        Set<String> pins=prefs.getStringSet("pinned",Collections.emptySet());
        for(int i=0;i<all.size();i++){labels[i]=all.get(i).title;selected[i]=pins.contains(all.get(i).pkg);}
        new AlertDialog.Builder(this).setTitle("Закреплённые приложения")
            .setMultiChoiceItems(labels,selected,(dialog,index,value)->selected[index]=value)
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(dialog,which)->{
                HashSet<String> p=new HashSet<>();for(int i=0;i<all.size();i++)if(selected[i])p.add(all.get(i).pkg);
                prefs.edit().putStringSet("pinned",p).apply();
                if(prefs.getBoolean("enabled",false))startPanel();
            }).show();
    }
}
