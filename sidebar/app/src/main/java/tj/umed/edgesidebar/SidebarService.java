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
    private View iconView,gestureView,gestureViewRight,panelView;
    public static final String ACTION_OPEN_PANEL="tj.umed.edgesidebar.lsposed.OPEN_PANEL";
    public static final String ACTION_REFRESH_CONFIG="tj.umed.edgesidebar.lsposed.REFRESH_CONFIG";
    private FrameLayout muteButton,dndButton;
    private ImageView muteImage,dndImage;
    public static final String ACTION_HOOK_GESTURE="tj.umed.edgesidebar.lsposed.ACTION_HOOK_GESTURE";
    private long lastHookGesture=0;
    private final BroadcastReceiver hookCommands=new BroadcastReceiver(){
      @Override public void onReceive(Context context,Intent intent){
        if(!ACTION_HOOK_GESTURE.equals(intent.getAction()) || !prefs.getBoolean("enabled",false) || !prefs.getBoolean("gesture",true))return;
        if(SystemClock.elapsedRealtime()-lastHookGesture<650)return;
        lastHookGesture=SystemClock.elapsedRealtime();
        String[] recents=intent.getStringArrayExtra("recents");
        if(recents!=null && recents.length>0){
          StringBuilder sb=new StringBuilder();
          for(String name:recents){if(name!=null && name.matches("[a-zA-Z0-9_.]{3,200}") && !name.equals(getPackageName())){
             if(sb.length()>0)sb.append('|');sb.append(name);
          }}
          prefs.edit().putString("hook_recents",sb.toString()).putLong("hook_recents_at",System.currentTimeMillis()).putLong("hook_active_at",System.currentTimeMillis()).apply();
        }else prefs.edit().putLong("hook_active_at",System.currentTimeMillis()).apply();
        int rawY=intent.getIntExtra("rawY",screenH()/2);
        // The side comes from the origin of the actual Back gesture.
        // Do not use the configured launcher-icon side here.
        boolean fromRight=intent.getBooleanExtra("gesture_from_right",right());
        showPanel(false,Math.max(dp(25),Math.min(rawY,screenH()-dp(25))),fromRight);
      }
    };
    private Runnable dismiss=()->hidePanel();
    private boolean panelOpen=false;
    private int lastY=0;
    private boolean panelRight=false;
    private static final int DARK=0xff353535, DARKER=0xff292929, BLUE=0xff078efa;
    private static final String CHANNEL="sidebar_service";

    @Override public void onCreate(){
        super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        prefs=getSharedPreferences("sidebar",MODE_PRIVATE);
        // User-controlled modes are independent of vendor ringer changes when DND toggles.
        if(!prefs.contains("mute_enabled"))
            prefs.edit().putBoolean("mute_enabled",audio.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL).apply();
        if(!prefs.contains("dnd_enabled"))
            prefs.edit().putBoolean("dnd_enabled",notifications.getCurrentInterruptionFilter()!=NotificationManager.INTERRUPTION_FILTER_ALL).apply();
        IntentFilter filter=new IntentFilter(ACTION_HOOK_GESTURE);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(hookCommands,filter,Context.RECEIVER_EXPORTED);
        else registerReceiver(hookCommands,filter);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!prefs.getBoolean("enabled",false) || !Settings.canDrawOverlays(this)){
            stopSelf();return START_NOT_STICKY;
        }
        startForeground(41,notification());
        String action=intent==null?null:intent.getAction();
        if(ACTION_OPEN_PANEL.equals(action)){
            if(panelOpen)touch();
            else showPanel(true,screenH()/2);
        }else if(ACTION_REFRESH_CONFIG.equals(action)){
            if(panelOpen)showPanel(false,lastY,panelRight);
            else rebuildHandles();
        }else if(!panelOpen)rebuildHandles();
        return START_STICKY;
    }
    private Notification notification(){
        if(Build.VERSION.SDK_INT>=26)notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"Edge Sidebar",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);
        open.putExtra(MainActivity.EXTRA_SETTINGS,true);
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
        return params(width,height,x,y,right());
    }
    private WindowManager.LayoutParams params(int width,int height,int x,int y,boolean rightSide){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,
            Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|(rightSide?Gravity.RIGHT:Gravity.LEFT);
        p.x=x;p.y=y;p.setTitle("Edge Sidebar");return p;
    }
    private void remove(View v){if(v!=null){try{wm.removeViewImmediate(v);}catch(Exception ignored){}}}
    private void rebuildHandles(){
        // Neither edge receives a visible handle. Fallback zones are opt-in.
        remove(iconView);remove(gestureView);remove(gestureViewRight);
        iconView=null;gestureView=null;gestureViewRight=null;
        if(!prefs.getBoolean("enabled",false)||!Settings.canDrawOverlays(this))return;
        if(prefs.getBoolean("gesture",true)&&prefs.getBoolean("edge_overlay_fallback",false)){
            int sh=screenH(),h=(int)(sh*0.58f),y=(sh-h)/2;
            try{
                gestureView=makeFallbackZone(false);
                wm.addView(gestureView,params(dp(20),h,0,y,false));
            }catch(Exception ignored){gestureView=null;}
            try{
                gestureViewRight=makeFallbackZone(true);
                wm.addView(gestureViewRight,params(dp(20),h,0,y,true));
            }catch(Exception ignored){gestureViewRight=null;}
        }
    }
    private View makeFallbackZone(final boolean fromRight){
        return new View(this){
            private float startX,startY;
            private boolean opened;
            @Override public boolean onTouchEvent(MotionEvent ev){
                switch(ev.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        startX=ev.getRawX();startY=ev.getRawY();opened=false;return true;
                    case MotionEvent.ACTION_MOVE:
                        if(opened)return true;
                        float dx=ev.getRawX()-startX;
                        if((fromRight && dx<-dp(26))||(!fromRight && dx>dp(26))){
                            opened=true;
                            showPanel(false,(int)startY,fromRight);
                        }
                        return true;
                    default:return true;
                }
            }
        };
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
        showPanel(iconLaunch,y,right());
    }
    private void showPanel(boolean iconLaunch,int y,boolean fromRight){
        handler.removeCallbacks(dismiss);
        remove(iconView);remove(gestureView);remove(gestureViewRight);
        iconView=null;gestureView=null;gestureViewRight=null;
        remove(panelView);panelView=null;panelOpen=true;
        lastY=y;
        panelRight=fromRight;

        final int appWidth=figApp(182),appHeight=figApp(802);
        final int volWidth=fig(142),volHeight=fig(802),gap=fig(28);
        final int fullWidth=appWidth+volWidth+gap;
        final int fullHeight=Math.max(appHeight,volHeight);

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setClipChildren(false);
        LinearLayout apps=makeAppRail();
        LinearLayout volume=makeVolumeRail();
        if(fromRight){
            outer.addView(apps,new LinearLayout.LayoutParams(appWidth,appHeight));
            addGap(outer,gap);
            outer.addView(volume,new LinearLayout.LayoutParams(volWidth,volHeight));
        }else{
            outer.addView(volume,new LinearLayout.LayoutParams(volWidth,volHeight));
            addGap(outer,gap);
            outer.addView(apps,new LinearLayout.LayoutParams(appWidth,appHeight));
        }
        int height=screenH();
        int center=(iconLaunch||prefs.getBoolean("fixed",false))?height/2:y;
        int maxTop=Math.max(0,height-fullHeight-fig(35));
        int top=Math.max(0,Math.min(center-fullHeight/2,maxTop));
        int preferredEdge=dp(Math.max(0,prefs.getInt("edge_offset_dp",4)));
        int edge=Math.min(preferredEdge,Math.max(0,screenW()-fullWidth));
        try{
            panelView=outer;
            wm.addView(panelView,params(fullWidth,fullHeight,edge,top,fromRight));
            touch();
        }catch(Exception ignored){
            panelView=null;panelOpen=false;rebuildHandles();
        }
    }
    private int fig(float figmaPixels){
        float scale=prefs==null?1f:prefs.getInt("scale_percent",100)/100f;
        return dp((figmaPixels/2.72f)*scale);
    }
    private int figApp(float figmaPixels){
        float appScale=Math.max(0.6f,Math.min(1.6f,prefs.getInt("app_scale_percent",100)/100f));
        return Math.max(1,Math.round(fig(figmaPixels)*appScale));
    }
    private static final int FIGMA_BG=0xff353535;
    private static final int FIGMA_IDLE=0xff323232;
    private static final int FIGMA_BLUE=0xff0086f8;
    private static final int FIGMA_PALE=0xffd9d9d9;
    private static final int FIGMA_ICON_IDLE=0xffd4d4d4;
    private static final int FIGMA_ICON_GREY=0xff6e6e6e;
    private void addGap(LinearLayout parent,int width){parent.addView(new View(this),new LinearLayout.LayoutParams(width,1));}
    private LinearLayout makeAppRail(){
        LinearLayout base=new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setBackground(MainActivity.round(FIGMA_BG,figApp(42)));
        base.setClipToOutline(true);
        base.setElevation(figApp(15));
        ScrollView scroll=new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout stack=new LinearLayout(this);
        stack.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setPadding(0,figApp(36),0,figApp(36));
        scroll.addView(stack,new ScrollView.LayoutParams(-1,-2));
        ArrayList<AppRepository.Item> items=AppRepository.ordered(this);
        if(items.isEmpty()){
            TextView placeholder=glyph("+",28,0xffeeeeee);
            placeholder.setOnClickListener(v->{
                hidePanel();
                startActivity(new Intent(this,MainActivity.class)
                    .putExtra(MainActivity.EXTRA_SETTINGS,true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(figApp(110),figApp(110));
            stack.addView(placeholder,ip);
        }
        for(AppRepository.Item app:items){
            ImageView icon=new ImageView(this);
            try{icon.setImageDrawable(getPackageManager().getApplicationIcon(app.pkg));}
            catch(Exception e){icon.setImageResource(android.R.drawable.sym_def_app_icon);}
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setContentDescription(app.title);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(figApp(110),figApp(110));
            ip.bottomMargin=figApp(36);
            stack.addView(icon,ip);
            icon.setOnClickListener(v->launchApp(app.pkg));
            icon.setOnLongClickListener(v->{
                AppRepository.togglePin(this,app.pkg);
                boolean pinned=prefs.getStringSet("pinned",Collections.emptySet()).contains(app.pkg);
                Toast.makeText(this,pinned?"Закреплено: "+app.title:"Откреплено: "+app.title,Toast.LENGTH_SHORT).show();
                showPanel(false,lastY);
                return true;
            });
        }
        View settings=new View(this){
            private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                super.onDraw(c);
                pen.setColor(0xff6e6e6e);
                float r=figApp(27);
                c.drawRoundRect(0,0,getWidth(),getHeight(),r,r,pen);
                pen.setColor(FIGMA_PALE);
                for(int i=0;i<3;i++)c.drawCircle(figApp(28+27*i),figApp(21),figApp(7.5f),pen);
            }
        };
        settings.setContentDescription("Настройки Edge Sidebar");
        settings.setOnClickListener(v->{
            hidePanel();
            startActivity(new Intent(this,MainActivity.class)
                .putExtra(MainActivity.EXTRA_SETTINGS,true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        stack.addView(settings,new LinearLayout.LayoutParams(figApp(110),figApp(42)));
        base.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        return base;
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
        LinearLayout rail=new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        VolumeSlider slider=new VolumeSlider(this);
        slider.setElevation(fig(15));
        rail.addView(slider,new LinearLayout.LayoutParams(fig(142),fig(554)));
        View gap1=new View(this);
        rail.addView(gap1,new LinearLayout.LayoutParams(1,fig(29)));
        boolean muteActive=prefs.getBoolean("mute_enabled",false);
        FrameLayout mute=createFigmaButton(muteActive?R.drawable.ic_figma_mute:R.drawable.ic_figma_bell,
            muteActive, muteActive?44f:45.5f, 47f,"Режим без звука",this::toggleRinger);
        muteButton=mute;muteImage=(ImageView)mute.getChildAt(0);
        rail.addView(mute,new LinearLayout.LayoutParams(fig(142),fig(95)));
        rail.addView(new View(this),new LinearLayout.LayoutParams(1,fig(29)));
        boolean dndActive=prefs.getBoolean("dnd_enabled",false);
        FrameLayout dnd=createFigmaButton(R.drawable.ic_figma_moon,dndActive,
            42f,45f,"Не беспокоить",this::toggleDnd);
        dndButton=dnd;dndImage=(ImageView)dnd.getChildAt(0);
        rail.addView(dnd,new LinearLayout.LayoutParams(fig(142),fig(95)));
        return rail;
    }
    private FrameLayout createFigmaButton(int drawable,boolean active,float iconWidth,float iconHeight,String label,Runnable action){
        FrameLayout frame=new FrameLayout(this);
        frame.setBackground(MainActivity.round(active?FIGMA_BLUE:FIGMA_IDLE,fig(42)));
        ImageView icon=new ImageView(this);
        icon.setImageResource(drawable);
        icon.setScaleType(ImageView.ScaleType.FIT_XY);
        icon.setColorFilter(active?Color.WHITE:FIGMA_ICON_IDLE);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(fig(iconWidth),fig(iconHeight),Gravity.CENTER);
        frame.addView(icon,ip);
        frame.setContentDescription(label);
        frame.setOnClickListener(v->{touch();action.run();});
        return frame;
    }
    /** Update pixels in existing window. Never remove or re-add the sidebar on a mode click. */
    private void renderModeButtons(){
        boolean mute=prefs.getBoolean("mute_enabled",false);
        boolean dnd=prefs.getBoolean("dnd_enabled",false);
        if(muteButton!=null && muteImage!=null){
            muteButton.setBackground(MainActivity.round(mute?FIGMA_BLUE:FIGMA_IDLE,fig(42)));
            muteImage.setImageResource(mute?R.drawable.ic_figma_mute:R.drawable.ic_figma_bell);
            muteImage.setColorFilter(mute?Color.WHITE:FIGMA_ICON_IDLE);
            FrameLayout.LayoutParams icon=(FrameLayout.LayoutParams)muteImage.getLayoutParams();
            icon.width=fig(mute?44f:45.5f);icon.height=fig(47);icon.gravity=Gravity.CENTER;
            muteImage.setLayoutParams(icon);
        }
        if(dndButton!=null && dndImage!=null){
            dndButton.setBackground(MainActivity.round(dnd?FIGMA_BLUE:FIGMA_IDLE,fig(42)));
            dndImage.setImageResource(R.drawable.ic_figma_moon);
            dndImage.setColorFilter(dnd?Color.WHITE:FIGMA_ICON_IDLE);
        }
    }
    private void toggleRinger(){
        boolean mute=!prefs.getBoolean("mute_enabled",false);
        try{
            audio.setRingerMode(mute?AudioManager.RINGER_MODE_SILENT:AudioManager.RINGER_MODE_NORMAL);
            prefs.edit().putBoolean("mute_enabled",mute).apply();
            renderModeButtons();
            touch();
        }catch(SecurityException e){requireDnd();}
    }
    private void toggleDnd(){
        if(!notifications.isNotificationPolicyAccessGranted()){requireDnd();return;}
        boolean enabled=!prefs.getBoolean("dnd_enabled",false);
        try{
            notifications.setInterruptionFilter(enabled?
                NotificationManager.INTERRUPTION_FILTER_NONE:NotificationManager.INTERRUPTION_FILTER_ALL);
            prefs.edit().putBoolean("dnd_enabled",enabled).apply();
            // MIUI may change ringer mode as a DND side-effect.
            // We deliberately leave mute_enabled and the mute button untouched.
            renderModeButtons();
            touch();
        }catch(SecurityException e){requireDnd();}
    }
    private void requireDnd(){
        Toast.makeText(this,"Предоставьте доступ к режиму «Не беспокоить»",Toast.LENGTH_LONG).show();
        try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(Exception ignored){}
    }
    /** Delegates to MIUI's own volume UI. The OEM panel owns the stream sliders. */
    private void showExpandedSound(){
        hidePanel();
        handler.postDelayed(()->{
            try{
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_SAME,AudioManager.FLAG_SHOW_UI);
            }catch(Exception error){
                try{
                    Intent setting=new Intent(Settings.ACTION_SOUND_SETTINGS);
                    setting.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(setting);
                }catch(Exception ignored){}
            }
        },65);
    }
    private final class VolumeSlider extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipShape=new Path();
        private final int max;
        private boolean threeDotsDown=false;
        VolumeSlider(Context c){
            super(c);
            max=Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            setContentDescription("Громкость медиа. Три точки открывают остальные настройки.");
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            float rad=fig(42);
            clipShape.reset();
            clipShape.addRoundRect(0,0,w,h,rad,rad,Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipShape);
            paint.setColor(FIGMA_BG);
            canvas.drawRect(0,0,w,h,paint);
            float fraction=Math.max(0,Math.min(1,audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)max));
            float start=h*(1-fraction);
            if(fraction>0){
                paint.setColor(FIGMA_PALE);
                canvas.drawRoundRect(0,start,w,h+rad,rad,rad,paint);
            }
            paint.setColor(FIGMA_ICON_GREY);
            for(int i=0;i<3;i++){
                canvas.drawCircle(fig(53+i*18),fig(46),fig(5),paint);
            }
            android.graphics.drawable.Drawable note=getDrawable(R.drawable.ic_figma_music);
            if(note!=null){
                note.setTint(FIGMA_ICON_GREY);
                note.setBounds(fig(51),fig(470),fig(91),fig(512));
                note.draw(canvas);
            }
            canvas.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    threeDotsDown=e.getY()<=fig(100);
                    touch();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if(threeDotsDown){
                        if(e.getY()>fig(130))threeDotsDown=false;
                        else return true;
                    }
                    setFromTouch(e.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                    if(threeDotsDown && e.getY()<=fig(100)){
                        touch();showExpandedSound();
                    }else setFromTouch(e.getY());
                    threeDotsDown=false;return true;
                case MotionEvent.ACTION_CANCEL:
                    threeDotsDown=false;return true;
            }
            return true;
        }
        private void setFromTouch(float y){
            touch();
            float fraction=1f-y/Math.max(1,getHeight());
            int value=Math.round(Math.max(0,Math.min(1,fraction))*max);
            try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);}
            catch(SecurityException ignored){}
            invalidate();
        }
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(panelOpen)hidePanel();else rebuildHandles();}
    @Override public void onDestroy(){
        handler.removeCallbacks(dismiss);try{unregisterReceiver(hookCommands);}catch(Exception ignored){}remove(iconView);remove(gestureView);remove(gestureViewRight);remove(panelView);
        iconView=null;gestureView=null;panelView=null;super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
