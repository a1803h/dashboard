package tj.umed.edgesidebar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Locale;

/**
 * Receives explicit broadcasts from injected MIUI Home / SystemUI code.
 * Manifest-registered so the last Back side is saved even when the floating
 * sidebar's foreground service is stopped or recreated.
 */
public final class BackSideReceiver extends BroadcastReceiver {
    public static final String ACTION="tj.umed.edgesidebar.lsposed.ACTION_LAST_BACK_SIDE";

    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null || !ACTION.equals(intent.getAction()) ||
                !intent.hasExtra("back_from_right"))return;
        final boolean right=intent.getBooleanExtra("back_from_right",false);
        final SharedPreferences prefs=context.getSharedPreferences("sidebar",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor=prefs.edit()
            .putBoolean("last_back_right",right)
            .putLong("last_back_seen_at",System.currentTimeMillis())
            .putString("last_back_source",intent.getStringExtra("back_source")==null?
                "unknown":intent.getStringExtra("back_source"))
            .putInt(right?"back_right_count":"back_left_count",
                prefs.getInt(right?"back_right_count":"back_left_count",0)+1)
            .putLong("hook_active_at",System.currentTimeMillis());
        String[] recents=intent.getStringArrayExtra("recents");
        if(recents!=null && recents.length>0) {
            StringBuilder sb=new StringBuilder();
            for(String pkg:recents){
                if(pkg==null || pkg.equals(context.getPackageName()) ||
                   !pkg.matches("[a-zA-Z0-9_.]{3,200}")) continue;
                if(sb.length()>0)sb.append('|');
                sb.append(pkg);
            }
            if(sb.length()>0) {
                editor.putString("hook_recents",sb.toString())
                    .putLong("hook_recents_at",System.currentTimeMillis());
            }
        }
        editor.apply();
    }
}
