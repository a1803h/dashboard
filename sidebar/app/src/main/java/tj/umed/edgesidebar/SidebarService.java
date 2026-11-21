package tj.umed.edgesidebar;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class SidebarService extends Service {
    private WindowManager wm;
    private AudioManager audio;
    private NotificationManager notifications;
    private android.content.SharedPreferences prefs;
    private Handler handler=new Handler(Looper.getMainLooper());
    private View iconView,gestureView,panelView;
    private Runnable dismiss=()->hidePanel();
    private boolean panelOpen=false;
    private int lastY=0;
    private static final int DARK=0xff353535, DARKER=0xff292929, BLUE=0xff078efa;
    private static final String CHANNEL="sidebar_service";

    @Override public void onCreate(){
        super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        prefs=getSharedPreferences("sidebar",MODE_PRIVATE);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!prefs.getBoolean("enabled",false) || !Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        startForeground(41,notification());
        if(panelOpen){hidePanel();}else rebuildHandles();
        return START_STICKY;
    }
    private Notification notification(){
        if(Build.VERSION.SDK_INT>=26)notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"Edge Sidebar",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("Edge Sidebar")
            .setContentText("Плавающая панель включена").setContentIntent(pi).setOngoing(true).build();
    }
    private int dp(float x){return MainActivity.dp(this,x);}
    private GradientDrawable shape(int color,int radius){return MainActivity.round(color,dp(radius));}
    private int screenW(){
        android.graphics.Point p=new android.graphics.Point();
        wm.getDefaultDisplay().getRealSize(p);return p.x;
    }
    private int screenH(){
        android.graphics.Point p=new android.graphics.Point();
        wm.getDefaultDisplay().getRealSize(p);return p.y;
    }
    private boolean right(){return prefs.getBoolean("right",false);}
    private WindowManager.LayoutParams params(int width,int height,int x,int y){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,
            Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|(right()?Gravity.RIGHT:Gravity.LEFT);
        p.x=x;p.y=y;p.setTitle("Edge Sidebar");return p;
    }
    private void remove(View v){if(v!=null){try{wm.removeViewImmediate(v);}catch(Exception ignored){}}}
    private void rebuildHandles(){
        remove(iconView);remove(gestureView);iconView=null;gestureView=null;
        if(!prefs.getBoolean("enabled",false) || !Settings.canDrawOverlays(this))return;
        LinearLayout tab=new LinearLayout(this);
        tab.setGravity(Gravity.CENTER);tab.setBackground(shape(0xe82b2c31,14));
        TextView arrow=new TextView(this);arrow.setText(right()?"‹":"›");
        arrow.setTextSize(26);arrow.setTextColor(0xffeeeeee);arrow.setGravity(Gravity.CENTER);
        tab.addView(arrow,new LinearLayout.LayoutParams(dp(26),dp(42)));
        tab.setContentDescription("Открыть Edge Sidebar");
        tab.setOnClickListener(v->showPanel(true,screenH()/2));
        try{iconView=tab;wm.addView(iconView,params(dp(26),dp(42),dp(22),screenH()/2-dp(21)));}catch(Exception ignored){iconView=null;}
        if(prefs.getBoolean("gesture",true)){
            View stripe=new View(this){
                private float sx,sy;
                @Override public boolean onTouchEvent(android.view.MotionEvent ev){
                    if(ev.getActionMasked()==MotionEvent.ACTION_DOWN){sx=ev.getRawX();sy=ev.getRawY();return true;}
                    if(ev.getActionMasked()==MotionEvent.ACTION_MOVE){
                        float delta=ev.getRawX()-sx;
                        if((right() && delta<-dp(26)) || (!right() && delta>dp(26))){
                            showPanel(false,(int)sy);return true;
                        }
                        return true;
                    }
                    return true;
                }
            };
            stripe.setBackgroundColor(Color.TRANSPARENT);
            int sh=screenH();int h=(int)(sh*0.58f);
            try{gestureView=stripe;wm.addView(gestureView,params(dp(20),h,0,(sh-h)/2));}catch(Exception ignored){gestureView=null;}
        }
    }
    private TextView glyph(String value,int size,int color){
        TextView v=new TextView(this);v.setText(value);v.setGravity(Gravity.CENTER);
        v.setTextSize(size);v.setTextColor(color);return v;
    }
    private View button(String symbol,int bgColor,int textColor,Runnable call){
        TextView t=glyph(symbol,23,textColor);t.setBackground(shape(bgColor,16));
        t.setOnClickListener(v->{touch();call.run();});return t;
    }
    private void touch(){handler.removeCallbacks(dismiss);handler.postDelayed(dismiss,12000);}
    private void hidePanel(){
        handler.removeCallbacks(dismiss);
        remove(panelView);panelView=null;panelOpen=false;
        rebuildHandles();
    }
    private void showPanel(boolean iconLaunch,int y){
        handler.removeCallbacks(dismiss);
        remove(iconView);remove(gestureView);
        iconView=null;gestureView=null;
        remove(panelView);panelView=null;panelOpen=true;
        lastY=y;
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);outer.setClipChildren(false);
        LinearLayout apps=makeAppRail();
        LinearLayout volume=makeVolumeRail();
        if(right()){
            outer.addView(apps,new LinearLayout.LayoutParams(dp(66),dp(360)));
            addGap(outer,dp(9));outer.addView(volume,new LinearLayout.LayoutParams(dp(52),dp(360)));
        }else{
            outer.addView(volume,new LinearLayout.LayoutParams(dp(52),dp(360)));
            addGap(outer,dp(9));outer.addView(apps,new LinearLayout.LayoutParams(dp(66),dp(360)));
        }
        int height=screenH();int center=(iconLaunch || prefs.getBoolean("fixed",false))?height/2:y;
        int top=Math.max(dp(35),Math.min(center-dp(180),height-dp(390)));
        try{panelView=outer;wm.addView(panelView,params(dp(127),dp(365),dp(10),top));touch();}
        catch(Exception ignored){panelView=null;panelOpen=false;rebuildHandles();}
    }
    private void addGap(LinearLayout parent,int width){parent.addView(new View(this),new LinearLayout.LayoutParams(width,1));}
    private LinearLayout makeAppRail(){
        LinearLayout base=new LinearLayout(this);base.setOrientation(LinearLayout.VERTICAL);
        base.setBackground(shape(DARK,17));base.setPadding(dp(7),dp(7),dp(7),dp(7));
        base.setElevation(dp(8));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout stack=new LinearLayout(this);stack.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        stack.setOrientation(LinearLayout.VERTICAL);scroll.addView(stack);
        ArrayList<AppRepository.Item> items=AppRepository.ordered(this);
        if(items.isEmpty()){
            TextView placeholder=glyph("+",28,0xffdddddd);
            placeholder.setOnClickListener(v->{
                hidePanel();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
            stack.addView(placeholder,new LinearLayout.LayoutParams(-1,dp(52)));
        }
        for(AppRepository.Item app:items){
            ImageView icon=new ImageView(this);
            try{icon.setImageDrawable(getPackageManager().getApplicationIcon(app.pkg));}
            catch(Exception e){icon.setImageResource(android.R.drawable.sym_def_app_icon);}
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setContentDescription(app.title);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(47),dp(47));
            ip.setMargins(0,dp(4),0,dp(8));stack.addView(icon,ip);
            icon.setOnClickListener(v->launchApp(app.pkg));
            icon.setOnLongClickListener(v->{
                AppRepository.togglePin(this,app.pkg);
                boolean pinned=prefs.getStringSet("pinned",Collections.emptySet()).contains(app.pkg);
                Toast.makeText(this,pinned?"Закреплено: "+app.title:"Откреплено: "+app.title,Toast.LENGTH_SHORT).show();
                showPanel(false,lastY);return true;
            });
        }
        base.addView(scroll,new LinearLayout.LayoutParams(-1,-1));return base;
    }
    private void launchApp(String pkg){
        try{
            Intent i=getPackageManager().getLaunchIntentForPackage(pkg);
            if(i==null)throw new IllegalStateException("App missing");
            AppRepository.markOpened(this,pkg);
            hidePanel();i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        }catch(Exception e){Toast.makeText(this,"Не удалось открыть приложение",Toast.LENGTH_SHORT).show();}
    }
    private LinearLayout makeVolumeRail(){
        LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.setPadding(dp(4),dp(5),dp(4),dp(8));top.setBackground(shape(DARK,16));top.setElevation(dp(7));
        TextView dots=glyph("•••",13,0xff959595);dots.setContentDescription("Другие громкости");
        dots.setOnClickListener(v->{touch();showExpandedSound();});
        top.addView(dots,new LinearLayout.LayoutParams(-1,dp(30)));
        VolumeSlider slider=new VolumeSlider(this);top.addView(slider,new LinearLayout.LayoutParams(dp(44),dp(215)));
        rail.addView(top,new LinearLayout.LayoutParams(-1,dp(258)));
        View mute=button(audio.getRingerMode()==AudioManager.RINGER_MODE_NORMAL?"♧":"♧",BLUE,Color.WHITE,this::toggleRinger);
        // Notification-bell glyph: use a system vector icon instead of a font-dependent character.
        ImageView muteIcon=new ImageView(this);muteIcon.setImageResource(android.R.drawable.ic_lock_silent_mode);
        muteIcon.setColorFilter(Color.WHITE);
        FrameLayout muteContainer=new FrameLayout(this);muteContainer.setBackground(shape(BLUE,18));
        FrameLayout.LayoutParams mi=new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER);
        muteContainer.addView(muteIcon,mi);
        muteContainer.setAlpha(audio.getRingerMode()==AudioManager.RINGER_MODE_NORMAL?0.65f:1f);
        muteContainer.setContentDescription("Режим без звука");
        muteContainer.setOnClickListener(v->{touch();toggleRinger();});
        LinearLayout.LayoutParams mb=new LinearLayout.LayoutParams(-1,dp(43));mb.topMargin=dp(11);rail.addView(muteContainer,mb);
        TextView dnd=glyph("☾",30,Color.WHITE);dnd.setBackground(shape(DARKER,17));
        int filter=notifications.getCurrentInterruptionFilter();
        dnd.setAlpha(filter==NotificationManager.INTERRUPTION_FILTER_ALL?0.72f:1f);
        dnd.setContentDescription("Не беспокоить");
        dnd.setOnClickListener(v->{touch();toggleDnd();});
        LinearLayout.LayoutParams dn=new LinearLayout.LayoutParams(-1,dp(43));dn.topMargin=dp(8);rail.addView(dnd,dn);
        return rail;
    }
    private void toggleRinger(){
        try{
            int next=audio.getRingerMode()==AudioManager.RINGER_MODE_NORMAL?AudioManager.RINGER_MODE_SILENT:AudioManager.RINGER_MODE_NORMAL;
            audio.setRingerMode(next);
            showPanel(false,lastY);
        }catch(SecurityException e){requireDnd();}
    }
    private void toggleDnd(){
        if(!notifications.isNotificationPolicyAccessGranted()){requireDnd();return;}
        try{
            int now=notifications.getCurrentInterruptionFilter();
            notifications.setInterruptionFilter(now==NotificationManager.INTERRUPTION_FILTER_ALL?
                NotificationManager.INTERRUPTION_FILTER_NONE:NotificationManager.INTERRUPTION_FILTER_ALL);
            showPanel(false,lastY);
        }catch(SecurityException e){requireDnd();}
    }
    private void requireDnd(){
        Toast.makeText(this,"Предоставьте доступ к режиму «Не беспокоить»",Toast.LENGTH_LONG).show();
        try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(Exception ignored){}
    }
    private void showExpandedSound(){
        LinearLayout stack=new LinearLayout(this);stack.setPadding(dp(20),dp(10),dp(20),dp(8));stack.setOrientation(LinearLayout.VERTICAL);
        addVolumeRow(stack,"Медиа",AudioManager.STREAM_MUSIC);
        addVolumeRow(stack,"Звонок",AudioManager.STREAM_RING);
        addVolumeRow(stack,"Уведомления",AudioManager.STREAM_NOTIFICATION);
        AlertDialog dialog=new AlertDialog.Builder(new ContextThemeWrapper(this,android.R.style.Theme_Material_Light_Dialog_Alert))
            .setTitle("Громкость").setView(stack)
            .setNeutralButton("Настройки звука",(d,w)->{
                hidePanel();
                try{startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception ignored){}
            })
            .setPositiveButton("Готово",null).create();
        if(dialog.getWindow()!=null)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        try{dialog.show();}catch(Exception e){Toast.makeText(this,"Невозможно открыть настройки громкости",Toast.LENGTH_SHORT).show();}
    }
    private void addVolumeRow(LinearLayout stack,String name,int stream){
        TextView label=glyph(name,15,Color.DKGRAY);label.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);stack.addView(label);
        SeekBar bar=new SeekBar(this);bar.setMax(audio.getStreamMaxVolume(stream));bar.setProgress(audio.getStreamVolume(stream));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int value,boolean user){
                if(user)try{audio.setStreamVolume(stream,value,0);}catch(SecurityException e){requireDnd();}
            }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });
        stack.addView(bar,new LinearLayout.LayoutParams(dp(260),dp(42)));
    }
    private final class VolumeSlider extends View {
        private Paint paint=new Paint(3);
        private int max;
        VolumeSlider(Context c){super(c);max=Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));setContentDescription("Громкость медиа");}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float x=dp(1),w=getWidth()-dp(2),y=dp(2),h=getHeight()-dp(4);
            paint.setColor(0xff424242);c.drawRoundRect(x,y,w,y+h,dp(14),dp(14),paint);
            float fraction=Math.max(0,Math.min(1,audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)max));
            float top=y+h*(1-fraction);
            paint.setColor(0xffd9d9d9);c.drawRoundRect(x,Math.max(top,y),w,y+h,dp(14),dp(14),paint);
            paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(dp(23));paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(0xff757575);c.drawText("♫",getWidth()/2f,getHeight()-dp(15),paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN || e.getActionMasked()==MotionEvent.ACTION_MOVE){
                touch();float f=1-e.getY()/Math.max(1,getHeight());
                int v=Math.round(Math.max(0,Math.min(1,f))*max);
                try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,v,0);}catch(SecurityException ignored){}
                invalidate();return true;
            }
            return e.getActionMasked()==MotionEvent.ACTION_UP || super.onTouchEvent(e);
        }
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(panelOpen)hidePanel();else rebuildHandles();}
    @Override public void onDestroy(){
        handler.removeCallbacks(dismiss);remove(iconView);remove(gestureView);remove(panelView);
        iconView=null;gestureView=null;panelView=null;super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
