package com.lerapporteur.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;

/**
 * Le son que joue CE téléphone (YouTube, une vidéo, un message vocal…), pris
 * à la source pour l'interprète vocal — l'équivalent du son de l'ordinateur
 * que l'application Windows lui donne.
 *
 * Pourquoi : le micro d'un téléphone efface de lui-même tout ce que le
 * téléphone joue (c'est l'annulation d'écho des appels). Une vidéo jouée sur
 * le téléphone n'arrivait donc JAMAIS à l'interprète : silence complet
 * (essai de Felly, 16/10/2026). Android ne donne ce son qu'avec l'accord du
 * client (fenêtre « Commencer » du système) et un service de premier plan.
 *
 * Le son de Rapporteur lui-même — la voix de l'interprète — est exclu : il ne
 * s'entendrait pas parler. Les appels (Teams, WhatsApp…) restent hors de
 * portée : Android protège leur son.
 */
public class ServiceSonTelephone extends Service {

    /** Celui qui porte le son à la page : l'activité, tant qu'elle vit. */
    interface Recepteur {
        void pret();
        void recevoir(String morceau);
        void arrete(String raison);
    }

    static volatile Recepteur recepteur;

    static final String RESULTAT = "resultat";
    static final String DONNEES = "donnees";
    /** Banc de la voix, débogage seulement : garder le son de Rapporteur dans la capture. */
    static final String ESSAI_INCLURE_APPLI = "essai_inclure_appli";
    /** 48 kHz, la cadence du mélangeur d'Android : aucune conversion en route. */
    private static final int TAUX = 48000;
    /** Un morceau toutes les 50 ms : assez fin pour la voix, léger pour la page. */
    private static final int ECHANTILLONS = TAUX / 20;
    private static final String CANAL = "son-telephone";
    private static final int AVIS = 2;

    private MediaProjection projection;
    private MediaProjection.Callback rappel;
    private AudioRecord capture;
    private Thread lecteur;
    private volatile boolean actif;
    /** Arrêt voulu par la page : rien à lui annoncer. */
    private volatile boolean arretDemande;

    @Override
    public void onCreate() {
        NotificationChannel canal = new NotificationChannel(
                CANAL, getString(R.string.canal_son_nom), NotificationManager.IMPORTANCE_LOW);
        canal.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(canal);
    }

    @Override
    public int onStartCommand(Intent intention, int drapeaux, int idDepart) {
        if (Build.VERSION.SDK_INT < 29 || intention == null || !intention.hasExtra(DONNEES)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        PendingIntent retour = PendingIntent.getActivity(
                this, 0, new Intent(this, ActivitePrincipale.class), PendingIntent.FLAG_IMMUTABLE);
        Notification avis = new Notification.Builder(this, CANAL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notif_son_titre))
                .setContentText(getString(R.string.notif_son_texte))
                .setOngoing(true)
                .setContentIntent(retour)
                .build();
        /* « microphone » en plus : la capture continue quand le client passe
           sur YouTube et que Rapporteur quitte l'écran. */
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(AVIS, avis, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(AVIS, avis, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            }
        } catch (RuntimeException e) {
            try {
                startForeground(AVIS, avis, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } catch (RuntimeException encore) {
                echouer("service");
                return START_NOT_STICKY;
            }
        }

        arreterCapture();
        arretDemande = false;
        try {
            int resultat = intention.getIntExtra(RESULTAT, 0);
            Intent donnees = Build.VERSION.SDK_INT >= 33
                    ? intention.getParcelableExtra(DONNEES, Intent.class)
                    : intention.getParcelableExtra(DONNEES);
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(resultat, donnees);
            if (projection == null) { echouer("refus"); return START_NOT_STICKY; }
            /* Le client arrête le partage depuis le système : la page repasse
               au micro seul, l'interprète continue. */
            rappel = new MediaProjection.Callback() {
                @Override
                public void onStop() { terminer("systeme"); }
            };
            projection.registerCallback(rappel, new Handler(Looper.getMainLooper()));

            AudioPlaybackCaptureConfiguration.Builder regle = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN);
            if (!(BuildConfig.DEBUG && intention.getBooleanExtra(ESSAI_INCLURE_APPLI, false))) {
                regle.excludeUid(android.os.Process.myUid());
            }
            AudioPlaybackCaptureConfiguration choix = regle.build();
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(TAUX)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();
            int minimum = AudioRecord.getMinBufferSize(TAUX, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            capture = new AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(Math.max(minimum * 4, TAUX * 2))
                    .setAudioPlaybackCaptureConfig(choix)
                    .build();
            capture.startRecording();
            if (capture.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                echouer("capture");
                return START_NOT_STICKY;
            }
        } catch (Exception e) {
            echouer("capture");
            return START_NOT_STICKY;
        }

        actif = true;
        final AudioRecord source = capture;
        lecteur = new Thread(() -> {
            short[] tampon = new short[ECHANTILLONS];
            byte[] octets = new byte[ECHANTILLONS * 2];
            while (actif) {
                int lus = source.read(tampon, 0, tampon.length);
                if (lus < 0) { break; }
                if (lus == 0) { continue; }
                for (int i = 0; i < lus; i++) {
                    octets[2 * i] = (byte) (tampon[i] & 0xff);
                    octets[2 * i + 1] = (byte) ((tampon[i] >> 8) & 0xff);
                }
                Recepteur r = recepteur;
                if (r != null) { r.recevoir(Base64.encodeToString(octets, 0, lus * 2, Base64.NO_WRAP)); }
            }
            if (actif) { new Handler(Looper.getMainLooper()).post(() -> terminer("capture")); }
        }, "son-telephone");
        lecteur.start();
        Recepteur r = recepteur;
        if (r != null) { r.pret(); }
        return START_NOT_STICKY;
    }

    /** La page n'en veut plus (interprète arrêté) : on se tait sans rien lui dire. */
    @Override
    public void onDestroy() {
        arretDemande = true;
        arreterCapture();
    }

    private void echouer(String raison) {
        Recepteur r = recepteur;
        if (r != null) { r.arrete(raison); }
        arretDemande = true;
        arreterCapture();
        stopSelf();
    }

    private void terminer(String raison) {
        boolean prevenir = !arretDemande && actif;
        arreterCapture();
        Recepteur r = recepteur;
        if (prevenir && r != null) { r.arrete(raison); }
        stopSelf();
    }

    private void arreterCapture() {
        actif = false;
        if (capture != null) {
            try { capture.stop(); } catch (Exception e) { /* déjà arrêtée */ }
            /* La lecture en cours se débloque à l'arrêt : on l'attend avant de
               rendre la capture, jamais les deux en même temps. */
            if (lecteur != null) {
                try { lecteur.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            try { capture.release(); } catch (Exception e) { /* déjà rendue */ }
            capture = null;
        }
        lecteur = null;
        if (projection != null) {
            MediaProjection fin = projection;
            projection = null;
            /* Notre propre arrêt ne doit pas passer pour celui du client. */
            try { if (rappel != null) { fin.unregisterCallback(rappel); } } catch (Exception e) { /* déjà retiré */ }
            try { fin.stop(); } catch (Exception e) { /* déjà close */ }
        }
        rappel = null;
    }

    @Override
    public IBinder onBind(Intent intention) {
        return null;
    }
}
