package tj.umed.edgesidebar;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import java.util.*;

public final class AppRepository {
    private AppRepository(){}
    static class Item {
        final String pkg,title; final long seen;
        Item(String p,String t,long s){pkg=p;title=t;seen=s;}
    }
    static ArrayList<Item> installed(Context c) {
        PackageManager pm=c.getPackageManager();ArrayList<Item> result=new ArrayList<>();
        for(ApplicationInfo app:pm.getInstalledApplications(0)){
            if(app.packageName.equals(c.getPackageName()))continue;
            if(pm.getLaunchIntentForPackage(app.packageName)==null)continue;
            result.add(new Item(app.packageName,pm.getApplicationLabel(app).toString(),0));
        }
        result.sort((a,b)->a.title.compareToIgnoreCase(b.title));return result;
    }
    static ArrayList<Item> ordered(Context c){
        PackageManager pm=c.getPackageManager();
        android.content.SharedPreferences p=c.getSharedPreferences("sidebar",Context.MODE_PRIVATE);
        Set<String> pinned=p.getStringSet("pinned",Collections.emptySet());
        HashMap<String,Long> recent=new HashMap<>();
        try{
            UsageStatsManager manager=(UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);
            long now=System.currentTimeMillis();
            UsageEvents events=manager.queryEvents(now-7L*24*60*60*1000,now);
            UsageEvents.Event e=new UsageEvents.Event();
            while(events!=null && events.hasNextEvent()){
                events.getNextEvent(e);
                if(e.getEventType()!=UsageEvents.Event.MOVE_TO_FOREGROUND &&
                    (Build.VERSION.SDK_INT<29 || e.getEventType()!=UsageEvents.Event.ACTIVITY_RESUMED))continue;
                String pkg=e.getPackageName();
                if(pkg!=null && !pkg.equals(c.getPackageName()))recent.merge(pkg,e.getTimeStamp(),Math::max);
            }
        }catch(Exception ignored){}
        String last=p.getString("last_package",null);
        if(last!=null)recent.merge(last,p.getLong("last_launch",0),Math::max);
        // System recent-task ordering is preferable to usage statistics when the hook supplies it.
        String hookValue=p.getString("hook_recents","");
        long hookAge=System.currentTimeMillis()-p.getLong("hook_recents_at",0);
        HashMap<String,Integer> hookOrder=new HashMap<>();
        if(!hookValue.isEmpty() && hookAge>=0 && hookAge<120000L){
            String[] names=hookValue.split("\\n");
            for(int i=0;i<names.length;i++)if(!hookOrder.containsKey(names[i]))hookOrder.put(names[i],i);
        }
        ArrayList<Map.Entry<String,Long>> ranked=new ArrayList<>(recent.entrySet());
        ranked.sort((a,b)->Long.compare(b.getValue(),a.getValue()));
        HashSet<String> merged=new HashSet<>(pinned);int n=0;
        for(String pkg:hookOrder.keySet())if(pm.getLaunchIntentForPackage(pkg)!=null)merged.add(pkg);
        for(Map.Entry<String,Long> e:ranked){
            if(n>=12)break;
            if(pm.getLaunchIntentForPackage(e.getKey())!=null){merged.add(e.getKey());n++;}
        }
        ArrayList<Item> out=new ArrayList<>();
        for(String pkg:merged){
            try{
                if(pm.getLaunchIntentForPackage(pkg)==null)continue;
                ApplicationInfo info=pm.getApplicationInfo(pkg,0);
                out.add(new Item(pkg,pm.getApplicationLabel(info).toString(),recent.getOrDefault(pkg,0L)));
            }catch(Exception ignored){}
        }
        out.sort((a,b)->{
            int value=Long.compare(b.seen,a.seen);
            return value!=0?value:a.title.compareToIgnoreCase(b.title);
        });
        return out;
    }
    static void markOpened(Context c,String pkg){
        c.getSharedPreferences("sidebar",Context.MODE_PRIVATE).edit().putString("last_package",pkg).putLong("last_launch",System.currentTimeMillis()).apply();
    }
    static void togglePin(Context c,String pkg){
        android.content.SharedPreferences prefs=c.getSharedPreferences("sidebar",Context.MODE_PRIVATE);
        HashSet<String> pins=new HashSet<>(prefs.getStringSet("pinned",Collections.emptySet()));
        if(!pins.remove(pkg))pins.add(pkg);
        prefs.edit().putStringSet("pinned",pins).apply();
    }
}
