package tj.umed.edgesidebar;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.UserHandle;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed 1.9.2 legacy Xposed API module.
 * Deliberately avoids hooking global permission checks or Android services.
 * If a ROM-specific method is absent, the target process continues unchanged.
 */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TARGET_APP="tj.umed.edgesidebar.lsposed";
    private static final String ACTION="tj.umed.edgesidebar.lsposed.ACTION_HOOK_GESTURE";
    private static final WeakHashMap<Object,Gesture> state=new WeakHashMap<>();
    private static volatile long lastSentElapsed=0;

    private static final class Gesture {
        float startX, startY;
        long downMillis;
        boolean aborted, triggered, edge;
        Gesture(float x,float y,long when,boolean atEdge){
            startX=x;startY=y;downMillis=when;edge=atEdge;
        }
    }

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if(!"com.miui.home".equals(lp.packageName) && !"com.android.systemui".equals(lp.packageName))return;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam param){
                Context ctx=(Context)param.args[0];
                if(ctx==null || !lp.packageName.equals(ctx.getPackageName()))return;
                try{install(ctx,lp);}catch(Throwable t){XposedBridge.log("EdgeSidebar hook install: "+t);}
            }
        });
    }

    private static void install(Context context,XC_LoadPackage.LoadPackageParam lp) {
        if("com.miui.home".equals(lp.packageName)){
            // MIUI 14 / HyperOS launcher gesture path, ROM-dependent.
            hookMethod(context,lp.classLoader,"com.miui.home.recents.GestureStubView","onTouchEvent");
        }else{
            // AOSP / vendor SystemUI back-gesture path; missing classes are harmless.
            hookMethod(context,lp.classLoader,"com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler","onMotionEvent");
            hookMethod(context,lp.classLoader,"com.android.systemui.statusbar.phone.EdgeBackGestureHandler","onMotionEvent");
        }
    }

    private static void hookMethod(Context ctx,ClassLoader loader,String className,String methodName) {
        Class<?> target=XposedHelpers.findClassIfExists(className,loader);
        if(target==null)return;
        try{
            XposedBridge.hookAllMethods(target,methodName,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(p.args.length==0 || !(p.args[0] instanceof MotionEvent))return;
                    process(ctx,p,(MotionEvent)p.args[0]);
                }
            });
            XposedBridge.log("EdgeSidebar: attached "+className+"."+methodName);
        }catch(Throwable t){XposedBridge.log("EdgeSidebar failed "+className+": "+t);}
    }

    private static void process(Context context, XC_MethodHook.MethodHookParam p, MotionEvent motion) {
        try{
            int event=motion.getActionMasked();
            final Object owner=p.thisObject;
            int screenWidth=context.getResources().getDisplayMetrics().widthPixels;
            float density=context.getResources().getDisplayMetrics().density;
            float startMargin=65*density;
            if(event==MotionEvent.ACTION_DOWN){
                float x=motion.getRawX();
                boolean edge=x<startMargin || x>screenWidth-startMargin;
                synchronized(state){state.put(owner,new Gesture(x,motion.getRawY(),SystemClock.uptimeMillis(),edge));}
                return;
            }
            Gesture g;
            synchronized(state){g=state.get(owner);}
            if(g==null || !g.edge)return;
            if(event==MotionEvent.ACTION_UP || event==MotionEvent.ACTION_CANCEL){
                synchronized(state){state.remove(owner);}
                if(g.triggered)consume(p);
                return;
            }
            if(event!=MotionEvent.ACTION_MOVE)return;
            if(g.triggered){consume(p);return;}
            float dx=motion.getRawX()-g.startX;
            float dy=motion.getRawY()-g.startY;
            long elapsed=SystemClock.uptimeMillis()-g.downMillis;
            float inward=g.startX<screenWidth/2f?dx:-dx;
            // Waiting on the edge before moving keeps ordinary quick Back swipes intact.
            if(elapsed<330){
                if(Math.abs(dx)>13*density || Math.abs(dy)>28*density)g.aborted=true;
                return;
            }
            if(g.aborted || Math.abs(dy)>115*density)return;
            if(inward<37*density)return;
            g.triggered=true;
            if(SystemClock.elapsedRealtime()-lastSentElapsed>650){
                lastSentElapsed=SystemClock.elapsedRealtime();
                sendGesture(context,Math.round(g.startY));
            }
            consume(p);
        }catch(Throwable ignored){
            // Never allow module errors to crash SystemUI or Launcher.
        }
    }

    private static void consume(XC_MethodHook.MethodHookParam p){
        try{
            if(p.method instanceof Method){
                Class<?> result=((Method)p.method).getReturnType();
                if(result==boolean.class || result==Boolean.class)p.setResult(Boolean.TRUE);
                else if(result==void.class)p.setResult(null);
            }
        }catch(Throwable ignored){}
    }

    private static void sendGesture(Context context,int rawY){
        Intent event=new Intent(ACTION);
        event.setPackage(TARGET_APP);
        event.putExtra("rawY",rawY);
        ArrayList<String> recent=readRecentTasks(context);
        event.putExtra("recents",recent.toArray(new String[0]));
        try{context.sendBroadcast(event);}catch(Throwable ignored){}
    }

    private static ArrayList<String> readRecentTasks(Context context){
        ArrayList<String> result=new ArrayList<>();
        try{
            Class<?> am=XposedHelpers.findClass("android.app.ActivityTaskManager",null);
            Object service=XposedHelpers.callStaticMethod(am,"getService");
            int user=(Integer)XposedHelpers.callStaticMethod(UserHandle.class,"myUserId");
            Object slice=XposedHelpers.callMethod(service,"getRecentTasks",24,0,user);
            Object raw=slice instanceof List ? slice:XposedHelpers.callMethod(slice,"getList");
            if(raw instanceof List){
                for(Object task:(List<?>)raw){
                    if(!(task instanceof ActivityManager.RecentTaskInfo))continue;
                    ActivityManager.RecentTaskInfo info=(ActivityManager.RecentTaskInfo)task;
                    ComponentName comp=info.topActivity;
                    if(comp==null && info.baseIntent!=null)comp=info.baseIntent.getComponent();
                    if(comp==null)continue;
                    String pkg=comp.getPackageName();
                    if(pkg!=null && !TARGET_APP.equals(pkg) && !result.contains(pkg))result.add(pkg);
                    if(result.size()>=16)break;
                }
            }
        }catch(Throwable ignored){
            // Real task access can be vendor-restricted even in an injected process.
        }
        return result;
    }
}
