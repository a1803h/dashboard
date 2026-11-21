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
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("sidebar",MODE_PRIVATE);render();}
    @Override protected void onResume(){super.onResume(); if(prefs!=null && prefs.getBoolean("enabled",false) && Settings.canDrawOverlays(this)) startPanel(); if(root!=null) render();}

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
            if("enabled".equals(key) && !checked){stopService(new Intent(this,SidebarService.class));}
            else if(prefs.getBoolean("enabled",false)) {
                if(Settings.canDrawOverlays(this))startPanel();else askOverlay();
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
        LinearLayout g=group();label(g,"Работа панели","Значок для открытия остаётся доступным независимо от настройки жестов.");
        switchRow(g,"Включить панель","Плавающий значок и сервис", "enabled",false);
        switchRow(g,"Запуск жестом","Свайп от левого или правого края в других приложениях","gesture",true);
        switchRow(g,"Фиксированная высота","Иначе появляется рядом с точкой начала жеста","fixed",false);
        switchRow(g,"Расположить справа","Выключено — слева","right",false);
        LinearLayout apps=group();label(apps,"Приложения","Закреплённые приложения всегда в списке. Остальные сортируются по последнему открытию без повторов.");
        addButton(apps,"Выбрать закреплённые приложения",this::choosePins);
        TextView tip=text("Удержание иконки в панели закрепляет или открепляет приложение. Список прокручивается.",12,muted);apps.addView(tip);
        LinearLayout rights=group();label(rights,"Разрешения","Нужно предоставить вручную в Android.");
        addButton(rights,"Поверх других окон  "+(Settings.canDrawOverlays(this)?"✓":"—"),this::askOverlay);
        addButton(rights,"История запуска  "+(hasUsageAccess()?"✓":"—"),()->{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));});
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        addButton(rights,"Не беспокоить  "+(nm.isNotificationPolicyAccessGranted()?"✓":"—"),this::askDnd);
        LinearLayout extra=group();label(extra,"Особенности","Три точки панели открывают отдельные ползунки громкости и системные настройки звука. Режим без звука и «Не беспокоить» независимы.");
        TextView disclaimer=text("Важно: открытый Android API не позволяет точно читать системный экран Recents и перехватывать настоящий жест «Назад». Панель использует историю открытия приложений и отдельную область жеста у края.",12,muted);extra.addView(disclaimer);
    }
    private void startPanel() {
        if(!Settings.canDrawOverlays(this))return;
        Intent s=new Intent(this,SidebarService.class);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);
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
