package tj.umed.edgesidebar;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
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

    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final class Gesture {
        float startX,startY,lastInward,lastY;
        boolean edge,candidate,triggered;
        Runnable pending;
        Gesture(float x,float y,boolean edge){
            startX=x;startY=y;lastY=y;this.edge=edge;
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
        try {
            int event=motion.getActionMasked();
            Object owner=p.thisObject;
            int screenWidth=context.getResources().getDisplayMetrics().widthPixels;
            float density=context.getResources().getDisplayMetrics().density;
            float edgeSize=65*density;
            if(event==MotionEvent.ACTION_DOWN){
                float x=motion.getRawX(),y=motion.getRawY();
                boolean edge=x<=edgeSize||x>=screenWidth-edgeSize;
                synchronized(state){state.put(owner,new Gesture(x,y,edge));}
                return;
            }
            Gesture g;
            synchronized(state){g=state.get(owner);}
            if(g==null||!g.edge)return;
            if(event==MotionEvent.ACTION_UP||event==MotionEvent.ACTION_CANCEL){
                cancelPending(g);
                synchronized(state){state.remove(owner);}
                if(g.triggered)consume(p); // Sidebar gesture replaces normal back only after hold.
                return;
            }
            if(event!=MotionEvent.ACTION_MOVE)return;
            if(g.triggered){consume(p);return;}
            float dx=motion.getRawX()-g.startX;
            float dy=motion.getRawY()-g.startY;
            float inward=(g.startX<screenWidth/2f)?dx:-dx;
            if(Math.abs(dy)>75*density || inward<0){
                cancelPending(g);
                g.candidate=false;
                return;
            }
            if(inward>=32*density){
                if(!g.candidate){
                    g.candidate=true;
                    g.lastInward=inward;
                    g.lastY=motion.getRawY();
                    scheduleHold(context,owner,g);
                }else if(Math.abs(inward-g.lastInward)>20*density){
                    // Finger is still dragging: hold starts when it pauses.
                    cancelPending(g);
                    g.lastInward=inward;
                    g.lastY=motion.getRawY();
                    scheduleHold(context,owner,g);
                }
            }else if(g.candidate && inward<18*density){
                cancelPending(g);
                g.candidate=false;
            }
        }catch(Throwable ignored){
            // Hooks must never crash the launcher or SystemUI.
        }
    }

    private static void cancelPending(Gesture gesture){
        Runnable work=gesture.pending;
        if(work!=null){MAIN.removeCallbacks(work);gesture.pending=null;}
    }
    private static void scheduleHold(Context context,Object owner,Gesture gesture){
        Runnable r=()->{
            synchronized(state){
                if(state.get(owner)!=gesture||!gesture.candidate||gesture.triggered)return;
                gesture.triggered=true;
                gesture.pending=null;
            }
            long now=SystemClock.elapsedRealtime();
            if(now-lastSentElapsed>650){
                lastSentElapsed=now;
                sendGesture(context,Math.round(gesture.lastY),gesture.startX>=context.getResources().getDisplayMetrics().widthPixels/2f);
            }
        };
        gesture.pending=r;
        // Swipe to start Back, then pause with finger still down. Unlike the
        // old code, a short fast swipe is not marked aborted and no new MOVE
        // events are required during the hold.
        MAIN.postDelayed(r,330);
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

    private static void sendGesture(Context context,int rawY,boolean fromRight){
        Intent event=new Intent(ACTION);
        event.setPackage(TARGET_APP);
        event.putExtra("rawY",rawY);
        // The down-point is authoritative, not the app's fixed left/right setting.
        event.putExtra("gesture_from_right",fromRight);
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
