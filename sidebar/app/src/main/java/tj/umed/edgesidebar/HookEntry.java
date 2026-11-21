package tj.umed.edgesidebar;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Passive LSPosed gesture observer.
 * Records which edge initiated a normal Back swipe.
 * Never shows a sidebar, consumes the gesture or changes its result.
 */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TARGET_APP="tj.umed.edgesidebar.lsposed";
    private static final String ACTION_BACK_SIDE="tj.umed.edgesidebar.lsposed.ACTION_LAST_BACK_SIDE";
    private static final WeakHashMap<Object,Gesture> gestures=new WeakHashMap<>();

    private static class Gesture {
        final float startX,startY;
        final boolean fromRight,edge;
        boolean recorded;
        Gesture(float x,float y,boolean right,boolean edge){
            startX=x;startY=y;fromRight=right;this.edge=edge;
        }
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp){
        if(!"com.miui.home".equals(lp.packageName) &&
           !"com.android.systemui".equals(lp.packageName))return;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,
            new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    Context ctx=(Context)p.args[0];
                    if(ctx==null || !lp.packageName.equals(ctx.getPackageName()))return;
                    try{
                        if("com.miui.home".equals(lp.packageName)){
                            hookTouch(ctx,lp.classLoader,"com.miui.home.recents.GestureStubView","onTouchEvent");
                        }else{
                            hookTouch(ctx,lp.classLoader,
                                "com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler",
                                "onMotionEvent");
                            hookTouch(ctx,lp.classLoader,
                                "com.android.systemui.statusbar.phone.EdgeBackGestureHandler",
                                "onMotionEvent");
                        }
                    }catch(Throwable t){XposedBridge.log("EdgeSidebar Back observer: "+t);}
                }
            });
    }

    private static void hookTouch(Context ctx,ClassLoader loader,String type,String method){
        Class<?> cls=XposedHelpers.findClassIfExists(type,loader);
        if(cls==null)return;
        try{
            XposedBridge.hookAllMethods(cls,method,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(p.args.length>0 && p.args[0] instanceof MotionEvent)
                        inspect(ctx,p.thisObject,(MotionEvent)p.args[0]);
                }
            });
            XposedBridge.log("EdgeSidebar observing Back side via "+type);
        }catch(Throwable t){
            XposedBridge.log("EdgeSidebar Back observer failed "+type+": "+t);
        }
    }

    private static void inspect(Context context,Object owner,MotionEvent ev){
        try{
            final int action=ev.getActionMasked();
            final float density=context.getResources().getDisplayMetrics().density;
            final int width=context.getResources().getDisplayMetrics().widthPixels;
            if(action==MotionEvent.ACTION_DOWN){
                float x=ev.getRawX();
                boolean fromRight=x>=width/2f;
                boolean edge=x<=65*density || x>=width-65*density;
                synchronized(gestures){
                    gestures.put(owner,new Gesture(x,ev.getRawY(),fromRight,edge));
                }
                return;
            }
            Gesture g;
            synchronized(gestures){g=gestures.get(owner);}
            if(g==null)return;
            if(action==MotionEvent.ACTION_CANCEL){
                synchronized(gestures){gestures.remove(owner);}
                return;
            }
            if(!g.edge){
                if(action==MotionEvent.ACTION_UP){
                    synchronized(gestures){gestures.remove(owner);}
                }
                return;
            }
            if(action==MotionEvent.ACTION_MOVE || action==MotionEvent.ACTION_UP){
                float inward=g.fromRight?g.startX-ev.getRawX():ev.getRawX()-g.startX;
                float vertical=Math.abs(ev.getRawY()-g.startY);
                if(!g.recorded && inward>=24*density && vertical<=70*density){
                    g.recorded=true;
                    // Sent on the first inward move; does not depend on a long press.
                    sendSide(context,g.fromRight);
                }
                if(action==MotionEvent.ACTION_UP){
                    synchronized(gestures){gestures.remove(owner);}
                }
            }
        }catch(Throwable ignored){
            // Failure must not affect Xiaomi navigation.
        }
    }

    private static void sendSide(Context context,boolean fromRight){
        Intent intent=new Intent(ACTION_BACK_SIDE);
        intent.setPackage(TARGET_APP);
        intent.putExtra("back_from_right",fromRight);
        // Preserve system task ordering used by the sidebar when it is available.
        ArrayList<String> recent=readRecentTasks(context);
        if(!recent.isEmpty())intent.putExtra("recents",recent.toArray(new String[0]));
        try{context.sendBroadcast(intent);}catch(Throwable ignored){}
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
