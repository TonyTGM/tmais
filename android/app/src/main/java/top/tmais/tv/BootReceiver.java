package top.tmais.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Android TV pode limitar a abertura de Activity a partir de broadcasts.
 * Para funcionamento estável em TVs compatíveis, selecione TMAIS TV como HOME.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) return;
        SharedPreferences prefs=context.getSharedPreferences("tmais_tv_settings",Context.MODE_PRIVATE);
        if (prefs.getString("server","").isEmpty()) return;
        try {
            Intent monitor = new Intent(context, WakeService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(monitor);
            else context.startService(monitor);
        } catch (RuntimeException e) {
            Log.w("TMAIS-TV","Não foi possível iniciar monitor de espera", e);
        }
        Intent launch=new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { context.startActivity(launch); }
        catch (RuntimeException e) { Log.w("TMAIS-TV","Inicializacao automatica bloqueada pelo aparelho",e); }
    }
}
