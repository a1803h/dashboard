package tj.umed.edgesidebar;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

/** Transparent HOME launcher entry: opens overlay directly, never adds a floating trigger. */
public final class LauncherActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        SharedPreferences prefs=getSharedPreferences("sidebar",MODE_PRIVATE);
        if(prefs.getBoolean("enabled",false) && Settings.canDrawOverlays(this)){
            Intent service=new Intent(this,SidebarService.class);
            service.setAction(SidebarService.ACTION_OPEN_PANEL);
            try{
                if(Build.VERSION.SDK_INT>=26)startForegroundService(service);
                else startService(service);
            }catch(IllegalStateException e){
                openSettings();
            }
        }else openSettings();
        finish();
        overridePendingTransition(0,0);
    }
    private void openSettings(){
        Intent settings=new Intent(this,MainActivity.class);
        settings.putExtra(MainActivity.EXTRA_SETTINGS,true);
        startActivity(settings);
    }
}
