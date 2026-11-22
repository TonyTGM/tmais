package top.tmais.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/**
 * Monitora o retorno do standby e tenta trazer o player à frente.
 * O Android pode bloquear a abertura de Activity a partir do background:
 * não há garantia em aparelhos Android TV/Google TV sem modo quiosque.
 */
public final class WakeService extends Service {
    private static final String TAG = "TMAIS-Wake";
    private static final String CHANNEL = "tmais_tv_autostart";
    private long lastAttempt = 0L;
    private boolean registered = false;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)) {
                attemptOpen();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "TMAIS TV • inicialização", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantém o monitor de retorno da TV em espera ativo.");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("TMAIS TV")
                .setContentText("Monitor de retorno automático ativado")
                .setOngoing(true)
                .setContentIntent(pending);
        startForeground(1102, builder.build());

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(screenReceiver, filter);
            }
            registered = true;
        } catch (Exception ex) {
            Log.e(TAG, "Não foi possível registrar monitor de tela", ex);
        }
    }

    private void attemptOpen() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastAttempt < 2500) return;
        lastAttempt = now;
        SharedPreferences prefs = getSharedPreferences("tmais_tv_settings", MODE_PRIVATE);
        if (prefs.getString("server", "").isEmpty()) return;
        Intent launch = new Intent(this, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(launch);
            Log.i(TAG, "Solicitada abertura do TMAIS TV ao ligar a tela");
        } catch (RuntimeException e) {
            Log.w(TAG, "O sistema bloqueou a abertura ao ligar a tela", e);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) {return null;}
    @Override public void onDestroy() {
        if (registered) {try {unregisterReceiver(screenReceiver);} catch (Exception ignored) {}}
        super.onDestroy();
    }
}
