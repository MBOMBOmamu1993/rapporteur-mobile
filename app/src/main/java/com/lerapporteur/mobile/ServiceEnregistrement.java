package com.lerapporteur.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Le service qui fait exister l'application : sans lui, Android suspend la
 * page — donc la capture — dès que l'écran s'éteint ou que le client passe à
 * autre chose pendant sa réunion. Déclaré « microphone », il dit au système
 * que le micro travaille, et la notification qu'il pose dit au client que
 * Rapporteur enregistre.
 *
 * Il ne capte RIEN lui-même : la capture vit dans la page, comme au
 * navigateur. Il ne fait que tenir le processus et le micro éveillés.
 */
public class ServiceEnregistrement extends Service {

    private static final String CANAL = "enregistrement";
    private PowerManager.WakeLock verrou;

    @Override
    public void onCreate() {
        NotificationChannel canal = new NotificationChannel(
                CANAL, getString(R.string.canal_nom), NotificationManager.IMPORTANCE_LOW);
        canal.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(canal);
    }

    @Override
    public int onStartCommand(Intent intention, int drapeaux, int idDepart) {
        PendingIntent retour = PendingIntent.getActivity(
                this, 0, new Intent(this, ActivitePrincipale.class), PendingIntent.FLAG_IMMUTABLE);
        Notification avis = new Notification.Builder(this, CANAL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notif_titre))
                .setContentText(getString(R.string.notif_texte))
                .setOngoing(true)
                .setContentIntent(retour)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, avis, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, avis);
        }

        /* Le processeur reste éveillé, l'écran peut s'éteindre. Huit heures de
           plafond : le site lui-même arrête la séance au palier des six heures. */
        if (verrou == null) {
            PowerManager alimentation = getSystemService(PowerManager.class);
            verrou = alimentation.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rapporteur:enregistrement");
        }
        if (!verrou.isHeld()) {
            verrou.acquire(8 * 60 * 60 * 1000L);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (verrou != null && verrou.isHeld()) { verrou.release(); }
    }

    @Override
    public IBinder onBind(Intent intention) {
        return null;
    }
}
