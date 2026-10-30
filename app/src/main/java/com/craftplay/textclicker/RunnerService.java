package com.craftplay.textclicker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.List;

public class RunnerService extends Service {
    public static boolean running = false;

    private static final String CHANNEL = "runner";
    private static final int NOTIF_ID = 1;
    private static final int MAX_TRIES = 20;      // 20 intentos x 500 ms = 10 s buscando el texto
    private static final long LATE_LIMIT = -60000; // más de 1 min tarde se omite

    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<ClickItem> items;
    private ClickItem current;
    private int index;
    private int tries;
    private PowerManager.WakeLock wakeLock;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        running = true;
        items = Store.load(this);
        index = 0;

        createChannel();
        Notification n = buildNotification("Iniciando...");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "textclicker:runner");
        wakeLock.acquire(24 * 60 * 60 * 1000L);

        Store.log(this, "Proceso iniciado (" + items.size() + " clicks)");
        next();
        return START_NOT_STICKY;
    }

    // Pasa al siguiente click de la fila
    private void next() {
        if (index >= items.size()) {
            Store.log(this, "Todos los clicks terminados");
            stopSelf();
            return;
        }
        current = items.get(index);
        long wait = current.millisUntil();
        if (wait < LATE_LIMIT) {
            Store.log(this, "Click " + (index + 1) + " omitido, la hora " + current.timeString() + " ya pasó");
            index++;
            next();
            return;
        }
        tries = 0;
        updateNotification("Esperando a click " + (index + 1) + ": \"" + current.text + "\" a las " + current.timeString());
        handler.postDelayed(attempt, Math.max(0, wait));
    }

    private final Runnable attempt = new Runnable() {
        @Override
        public void run() {
            ClickerService s = ClickerService.instance;
            boolean ok = s != null && s.clickText(current.text);
            if (ok) {
                Store.log(RunnerService.this, "Click " + (index + 1) + " OK: \"" + current.text + "\"");
                index++;
                next();
            } else if (++tries < MAX_TRIES) {
                updateNotification("Buscando \"" + current.text + "\" (click " + (index + 1) + ")");
                handler.postDelayed(this, 500);
            } else {
                String motivo = s == null ? "accesibilidad apagada" : "texto no encontrado | " + s.lastInfo;
                Store.log(RunnerService.this, "Click " + (index + 1) + " falló: \"" + current.text + "\" (" + motivo + ")");
                index++;
                next();
            }
        }
    };

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Proceso de clicks", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String msg) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
        b.setContentTitle("Text Clicker")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(pi);
        return b.build();
    }

    private void updateNotification(String msg) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(msg));
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        Store.log(this, "Proceso detenido");
        super.onDestroy();
    }
}
