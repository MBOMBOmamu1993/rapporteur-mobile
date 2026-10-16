package com.lerapporteur.mobile;

/**
 * Rapporteur pour Android — la coquille mobile.
 *
 * Comme l'application Windows, elle ne contient AUCUNE logique du service :
 * elle charge lerapporteur.com comme le ferait un navigateur, en y ajoutant la
 * seule chose qu'un navigateur de téléphone ne sait pas faire — continuer
 * d'enregistrer ÉCRAN ÉTEINT, par un service de premier plan « microphone ».
 *
 * Conséquences voulues de cette architecture :
 * - le design et les évolutions du site apparaissent ici sans mise à jour ;
 * - les clés, les prompts et la rédaction restent sur le serveur : il n'y a
 *   rien à voler dans cette application ;
 * - la session est le cookie du site : la sécurité est celle des routes API.
 *
 * L'application s'installe et s'ouvre librement ; enregistrer demande d'être
 * client — vérifié PAR LE SERVEUR à l'envoi, comme au navigateur.
 */

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ActivitePrincipale extends Activity {

    static final String SITE = "https://lerapporteur.com";
    private static final int DEMANDE_MICRO = 1;
    private static final int DEMANDE_REUNION = 2;
    private static final int CHOIX_FICHIERS = 3;
    private static final int SON_TELEPHONE = 4;

    private WebView toile;
    /* La réunion en ligne tenue SUR CE téléphone : Android interdit à deux
       APPLICATIONS de se partager le micro (Teams l'aurait, nous le silence),
       mais au sein d'UNE MÊME application, la réunion et l'enregistreur
       captent ensemble. D'où ce deuxième panneau : la réunion web en haut,
       la salle d'enregistrement en bas. */
    private WebView reunion;
    private LinearLayout colonne;
    private LinearLayout conteneurReunion;
    /* La demande de micro de la page, gardée le temps que l'utilisateur
       réponde à la fenêtre de permission d'Android. */
    private PermissionRequest demandeEnAttente;
    private PermissionRequest demandeReunion;
    /* Le choix de fichiers en cours (modèle, logos de /personnaliser) :
       la page attend sa réponse le temps que le sélecteur du téléphone
       fasse son office. */
    private android.webkit.ValueCallback<Uri[]> attenteFichiers;
    /* Le défi de la connexion en cours : armé au départ vers le navigateur,
       exigé par le serveur à l'échange du billet. Une application tierce qui
       écouterait rapporteur:// aurait le billet, jamais ce défi. */
    private String defi;

    /* Une erreur de page principale a été rapportée pendant le chargement en
       cours ; on tranche à onPageFinished (voir là). */
    private boolean echecPrincipal;
    private boolean relanceFaite;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::revenir);
        }

        toile = new WebView(this);
        WebSettings reglages = toile.getSettings();
        reglages.setJavaScriptEnabled(true);
        reglages.setDomStorageEnabled(true);
        /* Le klaxon de l'avertisseur de silence doit sonner sans clic. */
        reglages.setMediaPlaybackRequiresUserGesture(false);
        /* Rien du disque : la page n'a pas à lire de fichiers locaux. Les
           écrans embarqués (hors-ligne) vivent dans les assets, qui restent
           accessibles malgré ce réglage. */
        reglages.setAllowFileAccess(false);
        reglages.setAllowContentAccess(false);
        /* Verrous explicites contre l'injection — ce sont les défauts
           modernes, gravés ici pour qu'aucune mise à jour ne les rouvre :
           jamais de HTTP au milieu du HTTPS, et les écrans embarqués
           (file://) ne peuvent ni lire d'autres fichiers ni parler au web
           sous leur origine. */
        reglages.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        reglages.setAllowFileAccessFromFileURLs(false);
        reglages.setAllowUniversalAccessFromFileURLs(false);
        /* Google refuse sa page de connexion aux WebView déclarés (« ; wv »,
           « Version/4.0 ») : on signe comme le Chrome du téléphone — le mot
           « Android » reste, la salle d'enregistrement garde son mode
           téléphone. Sans cela, « Continuer avec Google » est impossible
           dans l'application. */
        reglages.setUserAgentString(reglages.getUserAgentString()
                .replace("; wv", "").replaceFirst("Version/\\d+\\.\\d+ ", ""));

        /* Le seul pont entre la page et l'application — l'équivalent du
           preload Electron : version, plateforme, et le service à tenir
           pendant la capture. Rien d'autre ne passe. */
        toile.addJavascriptInterface(new PontRapporteur(this), "rapporteurAndroid");

        toile.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView vue, WebResourceRequest requete) {
                Uri url = requete.getUrl();
                String schema = url.getScheme() == null ? "" : url.getScheme();

                /* Écrire à l'assistance depuis /aide : le courrieleur du
                   téléphone prend le relais. */
                if (schema.equals("mailto")) {
                    ouvrirDehors(new Intent(Intent.ACTION_SENDTO, url));
                    return true;
                }
                /* Les liens d'application (intent://…) : la page de connexion
                   Google en pose sur certains téléphones. Les avaler en
                   silence laissait le client dans un cul-de-sac — on tente
                   l'application visée, sinon la page de repli que le lien
                   transporte, et la connexion continue dans la WebView. */
                if (schema.equals("intent")) {
                    try {
                        Intent application = Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME);
                        String repli = application.getStringExtra("browser_fallback_url");
                        if (repli != null && repli.startsWith("https://")) {
                            vue.loadUrl(repli);
                        } else {
                            application.addCategory(Intent.CATEGORY_BROWSABLE);
                            application.setComponent(null);
                            application.setSelector(null);
                            startActivity(application);
                        }
                    } catch (Exception e) { /* rien à ouvrir : on reste sur la page */ }
                    return true;
                }
                if (!schema.equals("https") && !schema.equals("http")) {
                    ouvrirDehors(new Intent(Intent.ACTION_VIEW, url));
                    return true;
                }

                String hote = url.getHost() == null ? "" : url.getHost();
                if (hote.equals("lerapporteur.com") || hote.equals("www.lerapporteur.com")) {
                    /* L'accueil de l'application est /mobile, pas la page
                       commerciale : le clic sur la marque ramène ici — le
                       pendant du « retour-accueil » de l'application Windows. */
                    String chemin = url.getPath() == null ? "/" : url.getPath();
                    if (chemin.equals("/") || chemin.isEmpty()) {
                        vue.loadUrl(SITE + "/mobile");
                        return true;
                    }
                    if (chemin.equals("/en") || chemin.equals("/en/")) {
                        vue.loadUrl(SITE + "/en/mobile");
                        return true;
                    }
                    /* La connexion Google se joue dans le NAVIGATEUR du
                       téléphone : Google y est déjà connecté et ses
                       vérifications (« c'est bien vous ? », clé d'accès) y
                       aboutissent — dans une WebView, elles cassaient la
                       transaction en route. L'application arme un défi ;
                       la session reviendra par rapporteur://connexion, contre
                       billet ET défi (voir /api/connexion/mobile). */
                    if (chemin.equals("/api/connexion")) {
                        defi = fabriquerDefi();
                        String depart = url.toString()
                                + (url.getQuery() == null ? "?" : "&")
                                + "application=android&defi=" + defi;
                        ouvrirDehors(new Intent(Intent.ACTION_VIEW, Uri.parse(depart)));
                        return true;
                    }
                    /* Version Google Play : la règle « Paiements » interdit de
                       vendre dans l'application. Le site masque déjà tout
                       chemin d'achat ; ceci est la ceinture ET les bretelles —
                       même si sa page changeait, tarifs et caisse ne se
                       chargent jamais ici. */
                    if ("play".equals(BuildConfig.BOUTIQUE)
                            && (chemin.startsWith("/tarifs") || chemin.startsWith("/paiement")
                                || chemin.startsWith("/en/tarifs") || chemin.startsWith("/en/paiement"))) {
                        vue.loadUrl(SITE + (chemin.startsWith("/en/") ? "/en/mobile" : "/mobile"));
                        return true;
                    }
                    return false;
                }
                /* Version Google Play : aucune caisse de paiement ne se charge,
                   ni ici ni dans le navigateur — le lien reste lettre morte. */
                if ("play".equals(BuildConfig.BOUTIQUE)
                        && (hote.endsWith(".stripe.com") || hote.endsWith(".cinetpay.com"))) {
                    return true;
                }
                /* La connexion Google et le paiement se font dans
                   l'application : ces parcours doivent revenir vers le site
                   AVEC sa session — dans un navigateur externe, le client
                   serait connecté dans Chrome et pas ici. accounts.youtube.com
                   et *.gstatic.com font partie du parcours Google sur
                   certains téléphones. */
                if (hote.equals("accounts.google.com") || hote.equals("accounts.youtube.com")
                        || hote.endsWith(".gstatic.com")
                        || hote.endsWith(".stripe.com") || hote.endsWith(".cinetpay.com")) {
                    return false;
                }
                /* Tout autre site s'ouvre dans le vrai navigateur :
                   l'application ne montre que lerapporteur.com. */
                ouvrirDehors(new Intent(Intent.ACTION_VIEW, url));
                return true;
            }

            @Override
            public void onPageStarted(WebView vue, String url, android.graphics.Bitmap favicon) {
                echecPrincipal = false;
            }

            @Override
            public void onReceivedError(WebView vue, WebResourceRequest requete, WebResourceError erreur) {
                /* Une erreur de page principale n'est PAS toujours une page
                   absente. Hors connexion, le site garde ses écrans sur
                   l'appareil (service worker sw.js) ; la tentative réseau qui
                   précède la copie échoue, et le WebView la rapporte ici —
                   AVANT que la copie ne s'affiche. Sauter tout de suite sur
                   l'écran hors ligne, c'était enterrer la page qui arrivait
                   (02/09/2026 : « Pas de connexion » alors que tout était en
                   cache). On note l'échec et on juge à la fin du chargement. */
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("Rapporteur", "onReceivedError principal=" + requete.isForMainFrame()
                            + " code=" + erreur.getErrorCode() + " " + erreur.getDescription()
                            + " url=" + requete.getUrl() + " page=" + vue.getUrl());
                }
                if (requete.isForMainFrame()) {
                    /* Banc (débogage) : `--ez ancien true` rejoue le réflexe
                       d'avant — l'écran hors ligne sans attendre. */
                    if (BuildConfig.DEBUG && getIntent().getBooleanExtra("ancien", false)) {
                        vue.loadUrl("file:///android_asset/hors-ligne.html");
                        return;
                    }
                    echecPrincipal = true;
                }
            }

            @Override
            public void onPageFinished(WebView vue, String url) {
                if (!echecPrincipal || url == null || url.startsWith("file:")) { relanceFaite = false; return; }
                echecPrincipal = false;
                /* Ce qui s'est affiché : notre page (elle charge toujours
                   /js/langue.js) — rien à faire ; la page d'erreur du moteur
                   — l'écran hors ligne, comme l'accueil de secours de
                   l'application Windows. */
                vue.evaluateJavascript(
                        "!!document.querySelector('script[src*=\"/js/langue.js\"]')",
                        resultat -> {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.d("Rapporteur", "onPageFinished après échec : notre page = " + resultat);
                            }
                            if ("true".equals(resultat)) { relanceFaite = false; return; }
                            /* Au tout premier chargement, le moteur peut ne pas
                               avoir encore ouvert la réserve du service : une
                               seule relance, un instant plus tard. */
                            if (!relanceFaite && url.startsWith(SITE)) {
                                relanceFaite = true;
                                if (BuildConfig.DEBUG) { android.util.Log.d("Rapporteur", "relance unique de " + url); }
                                vue.postDelayed(() -> vue.loadUrl(url), 400);
                                return;
                            }
                            vue.loadUrl("file:///android_asset/hors-ligne.html");
                        });
            }

            @Override
            public boolean onRenderProcessGone(WebView vue, android.webkit.RenderProcessGoneDetail detail) {
                /* Le moteur du WebView est mort (mémoire, mise à jour du
                   système…) : sans ce geste, l'application resterait un écran
                   blanc qui « ne s'ouvre plus ». On repart proprement. */
                recreate();
                return true;
            }
        });

        toile.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest demande) {
                /* Le micro, uniquement, et uniquement pour notre site : la
                   page d'un tiers (caisse de paiement) n'obtient rien. */
                boolean pourNous = demande.getOrigin() != null
                        && (SITE.equals(demande.getOrigin().toString().replaceAll("/$", ""))
                            /* Constructions de débogage : le banc d'essai du
                               micro vit dans les assets. Jamais en production. */
                            || (BuildConfig.DEBUG
                                && demande.getOrigin().toString().startsWith("file:")));
                boolean veutMicro = false;
                for (String ressource : demande.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(ressource)) { veutMicro = true; }
                }
                if (!pourNous || !veutMicro) { demande.deny(); return; }

                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    demande.grant(new String[] { PermissionRequest.RESOURCE_AUDIO_CAPTURE });
                    return;
                }
                /* Android doit d'abord donner le micro à l'application. On
                   demande aussi les notifications : celle du service en a
                   besoin pour se montrer pendant la séance. */
                demandeEnAttente = demande;
                String[] permissions = Build.VERSION.SDK_INT >= 33
                        ? new String[] { Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS }
                        : new String[] { Manifest.permission.RECORD_AUDIO };
                requestPermissions(permissions, DEMANDE_MICRO);
            }

            /* « Déposez vos fichiers » (/personnaliser) : sans ce crochet, un
               WebView ne sait PAS ouvrir le sélecteur du téléphone — le clic
               ne faisait rien. Panne constatée en clientèle le 25/08/2026. */
            @Override
            public boolean onShowFileChooser(WebView vue,
                    android.webkit.ValueCallback<Uri[]> rappel, FileChooserParams reglagesChoix) {
                if (attenteFichiers != null) { attenteFichiers.onReceiveValue(null); }
                attenteFichiers = rappel;
                Intent choix = new Intent(Intent.ACTION_GET_CONTENT);
                choix.addCategory(Intent.CATEGORY_OPENABLE);
                choix.setType("*/*");
                /* La page dit ce qu'elle accepte (PDF, Word, images) : on le
                   transmet quand ce sont de vrais types — les extensions
                   nues, le sélecteur ne les comprend pas. */
                java.util.List<String> types = new java.util.ArrayList<>();
                for (String type : reglagesChoix.getAcceptTypes()) {
                    if (type != null && type.contains("/")) { types.add(type.trim()); }
                }
                if (!types.isEmpty()) {
                    choix.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
                }
                choix.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                        reglagesChoix.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(choix, CHOIX_FICHIERS);
                } catch (Exception e) {
                    attenteFichiers = null;
                    return false;
                }
                return true;
            }
        });

        /* Les documents (exemple de compte rendu, reçus) se téléchargent avec
           la session du site, dans le dossier Téléchargements du téléphone. */
        toile.setDownloadListener((url, agent, disposition, type, taille) -> {
            try {
                DownloadManager.Request demande = new DownloadManager.Request(Uri.parse(url));
                String biscuit = CookieManager.getInstance().getCookie(url);
                if (biscuit != null) { demande.addRequestHeader("Cookie", biscuit); }
                demande.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                demande.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        Uri.parse(url).getLastPathSegment());
                getSystemService(DownloadManager.class).enqueue(demande);
            } catch (Exception e) { /* lien inhabituel : on n'emporte pas l'application */ }
        });

        /* Depuis Android 15, l'application dessine sous les barres du système :
           on rend au contenu la place qu'elles occupent, sur un fond assorti.
           La colonne porte deux étages : la réunion (fermée par défaut) et la
           salle d'enregistrement. */
        colonne = new LinearLayout(this);
        colonne.setOrientation(LinearLayout.VERTICAL);
        colonne.addView(toile, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        FrameLayout cadre = new FrameLayout(this);
        cadre.setBackgroundColor(getColor(R.color.fond));
        cadre.addView(colonne);
        if (Build.VERSION.SDK_INT >= 35) {
            cadre.setOnApplyWindowInsetsListener((vue, insets) -> {
                android.graphics.Insets barres = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                vue.setPadding(barres.left, barres.top, barres.right, barres.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        setContentView(cadre);

        /* Le site garde les écrans de l'application sur l'appareil (service
           worker sw.js) : c'est ce qui permet d'ouvrir et d'enregistrer sans
           réseau. Dans un WebView qui vient de naître, la TOUTE PREMIÈRE
           navigation partait avant que ce service ne soit réveillé, et hors
           ligne elle échouait — écran « Pas de connexion » alors que tout
           était en cache (02/09/2026). On réveille le service avant de
           charger ; et si la première page échoue quand même, onPageFinished
           la relance une fois (voir là). */
        try { android.webkit.ServiceWorkerController.getInstance(); } catch (Throwable e) { /* WebView sans service worker */ }
        toile.loadUrl(SITE + "/mobile");
        /* Lancée par le lien de retour de connexion alors qu'elle était
           fermée : on tente l'échange — sans défi, le serveur ramènera à la
           connexion, et le client recommencera d'un geste. */
        traiterLienConnexion(getIntent());

        /* Banc d'essai du panneau réunion, constructions de DÉBOGAGE
           seulement : `adb shell am start … --es essai_reunion <url>` ouvre
           le panneau sans passer par la salle — c'est ainsi qu'on éprouve la
           chaîne micro complète (fenêtre Android comprise) sans compte. */
        if (BuildConfig.DEBUG) {
            String essai = getIntent().getStringExtra("essai_reunion");
            if (essai != null) {
                toile.postDelayed(() -> ouvrirReunion(essai), 6000);
            }
            if (getIntent().hasExtra("essai_micro")) {
                toile.loadUrl("file:///android_asset/essai-micro.html");
            }
            if (getIntent().hasExtra("essai_fichier")) {
                toile.loadUrl("file:///android_asset/essai-fichier.html");
            }
            if (getIntent().hasExtra("essai_son")) {
                toile.loadUrl("file:///android_asset/essai-son.html");
            }
            /* La prise du son de la réunion : la salle d'essai en bas, une réunion
               d'essai (WebRTC bouclé, joué par un élément audio) dans le panneau. */
            if (getIntent().hasExtra("essai_prise")) {
                toile.loadUrl("file:///android_asset/essai-prise.html");
                toile.postDelayed(() -> ouvrirReunion("file:///android_asset/essai-reunion-son.html"), 3000);
            }
            /* Le volume d'appel de l'interprète : l'émulateur n'a pas d'annulation
               d'écho matérielle, donc jamais de mode « appel » venu du WebView — on
               le pose ici comme lui, puis on suit le volume (traces D/Rapporteur).
               `--ez essai_volume_appel true` : mode posé avant l'interprète ;
               `--ez essai_volume_appel_apres true` : mode posé après lui. */
            boolean volumeAvant = getIntent().hasExtra("essai_volume_appel");
            if (Build.VERSION.SDK_INT >= 31 && (volumeAvant || getIntent().hasExtra("essai_volume_appel_apres"))) {
                AudioManager audio = getSystemService(AudioManager.class);
                Runnable modeAppel = () -> {
                    audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    for (android.media.AudioDeviceInfo d : audio.getAvailableCommunicationDevices()) {
                        if (d.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { audio.setCommunicationDevice(d); }
                    }
                };
                java.util.function.Consumer<String> trace = (quand) -> android.util.Log.d("Rapporteur",
                        "volume d'appel " + quand + " : " + audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                        + "/" + audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) + ", mode " + audio.getMode());
                if (volumeAvant) { modeAppel.run(); }
                audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 1, 0);
                trace.accept("au départ");
                autresSons(true);
                toile.postDelayed(() -> {
                    if (!volumeAvant) { modeAppel.run(); }
                    toile.postDelayed(() -> {
                        trace.accept("pendant l'interprète");
                        autresSons(false);
                        toile.postDelayed(() -> {
                            trace.accept("après l'interprète");
                            audio.clearCommunicationDevice();
                            audio.setMode(AudioManager.MODE_NORMAL);
                        }, 1000);
                    }, 1500);
                }, 1500);
            }
        }
    }

    /** Le retour de la connexion : rapporteur://connexion?billet=… — le
     *  parcours Google s'est joué dans le navigateur, la session naît ici. */
    @Override
    protected void onNewIntent(Intent intention) {
        super.onNewIntent(intention);
        traiterLienConnexion(intention);
    }

    private void traiterLienConnexion(Intent intention) {
        Uri lien = intention == null ? null : intention.getData();
        if (lien == null || !"rapporteur".equals(lien.getScheme())
                || !"connexion".equals(lien.getHost())) { return; }
        String billet = lien.getQueryParameter("billet");
        if (billet == null || billet.isEmpty()) { return; }
        toile.loadUrl(SITE + "/api/connexion/mobile?billet=" + Uri.encode(billet)
                + "&defi=" + (defi == null ? "" : defi));
        defi = null;
    }

    private static String fabriquerDefi() {
        byte[] graine = new byte[24];
        new java.security.SecureRandom().nextBytes(graine);
        StringBuilder texte = new StringBuilder();
        for (byte octet : graine) { texte.append(String.format("%02x", octet)); }
        return texte.toString();
    }

    /** Le service qui tient le micro éveillé, démarré par le pont quand la
     *  page lance la capture. On vérifie que c'est bien NOTRE page qui parle :
     *  le pont est offert à tout ce que le WebView affiche. */
    void demarrerService() {
        runOnUiThread(() -> {
            if (!pageDeChezNous(toile.getUrl())) { return; }
            startForegroundService(new Intent(this, ServiceEnregistrement.class));
        });
    }

    /** La page affichée est-elle vraiment la nôtre ? Comparaison EXACTE du
     *  schéma et de l'hôte — un préfixe (« startsWith ») laisserait passer
     *  « lerapporteur.com.pirate.tld ». C'est la serrure des gestes que le
     *  pont accorde, car il est offert à tout ce que le WebView affiche. */
    private static boolean pageDeChezNous(String adresse) {
        if (adresse == null) { return false; }
        try {
            Uri u = Uri.parse(adresse);
            return "https".equals(u.getScheme())
                    && (u.getPort() == -1 || u.getPort() == 443)
                    && ("lerapporteur.com".equals(u.getHost())
                        || "www.lerapporteur.com".equals(u.getHost()));
        } catch (Exception e) { return false; }
    }

    void arreterService() {
        runOnUiThread(() -> {
            if (!pageDeChezNous(toile.getUrl())) { return; }
            stopService(new Intent(this, ServiceEnregistrement.class));
        });
    }

    private void ouvrirDehors(Intent intention) {
        try { startActivity(intention); } catch (Exception e) { /* rien pour l'ouvrir : tant pis */ }
    }

    /* --- La réunion en ligne, tenue DANS l'application ---------------------
       Android interdit à deux applications de se partager le micro : une
       réunion Teams tenue dans SON application rend l'enregistreur sourd. Au
       sein d'une même application, en revanche, la réunion (en version web)
       et la salle d'enregistrement captent le micro ENSEMBLE : la réunion
       entend le client, l'enregistreur entend le client ET le haut-parleur. */

    /** Les services de réunion auxquels le panneau accorde micro et caméra.
     *  Tout autre site s'affiche mais n'obtient aucun capteur. */
    private static boolean hoteDeReunion(String hote) {
        if (hote == null) { return false; }
        return hote.equals("meet.google.com")
                || hote.equals("teams.microsoft.com") || hote.endsWith(".teams.microsoft.com")
                || hote.equals("teams.live.com") || hote.endsWith(".teams.live.com")
                || hote.equals("zoom.us") || hote.endsWith(".zoom.us")
                || hote.endsWith(".webex.com")
                || hote.equals("meet.jit.si")
                || hote.equals("whereby.com") || hote.endsWith(".whereby.com");
    }

    /** Appelé par le pont : la salle d'enregistrement demande d'ouvrir la
     *  réunion ici. Panneau du haut, sans pont natif — la page de réunion
     *  n'obtient rien d'autre que ses capteurs. */
    void ouvrirReunion(String brut) {
        runOnUiThread(() -> {
            try {
                if (!pageAutorisee(toile.getUrl())) { return; }
                Uri lien = Uri.parse(brut == null ? "" : brut.trim());
                boolean essai = BuildConfig.DEBUG && lien.toString().startsWith("file:///android_asset/");
                if (!"https".equals(lien.getScheme()) && !essai) { return; }
                if (reunion == null) { creerPanneauReunion(); }
                conteneurReunion.setVisibility(View.VISIBLE);
                panneauOuvert = true;
                reunion.loadUrl(lien.toString());
            } catch (Exception e) {
                /* Quoi qu'il arrive, le panneau ne doit jamais emporter la
                   salle d'enregistrement avec lui. */
            }
        });
    }

    void fermerReunion() {
        runOnUiThread(() -> {
            try {
                if (reunion == null) { return; }
                panneauOuvert = false;
                urlReunion = null;
                if (priseReunion) {
                    priseReunion = false;
                    direSonReunion("arrete", "");
                }
                reunion.loadUrl("about:blank");
                conteneurReunion.setVisibility(View.GONE);
            } catch (Exception e) { /* même règle : la salle avant tout */ }
        });
    }

    /*
     * Le son de la réunion ouverte dans le panneau, pour l'interprète (demande
     * de Felly, 16/10/2026 : « donner la voix à l'interprète » quand le lien de
     * la réunion est ouvert dans Rapporteur). La réunion est un son d'appel :
     * Android ne le laisse capter par aucune application, et par le micro
     * l'interprète se serait entendu lui-même. La prise (assets/prise-reunion.js),
     * posée dans la page de réunion, rend le son des CORRESPONDANTS — les flux
     * qu'elle reçoit —, jamais le micro du client, et seulement quand la salle
     * le demande. Ce que la page envoie est vérifié avant d'entrer dans la
     * salle : du base 64, de taille bornée, d'un service de réunion connu.
     */
    private volatile boolean panneauOuvert;
    private volatile String urlReunion;
    private volatile boolean priseReunion;
    private volatile double niveauReunion = 1;
    private String scriptPrise;
    private static final java.util.regex.Pattern BASE64 = java.util.regex.Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    /** Une page du panneau à qui l'on prend le son : un service de réunion en
     *  HTTPS (banc d'essai : les assets, en débogage seulement). */
    private static boolean reunionDeConfiance(String adresse) {
        if (adresse == null) { return false; }
        if (BuildConfig.DEBUG && adresse.startsWith("file:///android_asset/")) { return true; }
        try {
            Uri u = Uri.parse(adresse);
            return "https".equals(u.getScheme()) && hoteDeReunion(u.getHost());
        } catch (Exception e) { return false; }
    }

    /** La salle peut-elle demander le son de la réunion (panneau ouvert sur un service connu) ? */
    boolean sonReunionPossible() {
        return panneauOuvert && reunionDeConfiance(urlReunion);
    }

    /** La salle demande (ou lâche) le son de la réunion ; niveau = celui de la
     *  réunion dans le panneau pendant ce temps (1 à l'arrêt). */
    void sonReunion(boolean capter, double niveau) {
        runOnUiThread(() -> {
            if (!pageAutorisee(toile.getUrl())) { return; }
            priseReunion = capter && sonReunionPossible();
            niveauReunion = Math.max(0, Math.min(1, niveau));
            commanderPrise();
        });
    }

    private void commanderPrise() {
        if (reunion == null) { return; }
        reunion.evaluateJavascript("window.__rapporteurPrise&&window.__rapporteurPrise.capter("
                + priseReunion + "," + (priseReunion ? niveauReunion : 1) + ")", null);
    }

    private void poserPrise(WebView vue, String adresse) {
        urlReunion = adresse;
        if (!reunionDeConfiance(adresse)) { return; }
        try {
            if (scriptPrise == null) {
                try (java.io.InputStream entree = getAssets().open("prise-reunion.js")) {
                    java.io.ByteArrayOutputStream lu = new java.io.ByteArrayOutputStream();
                    byte[] tampon = new byte[8192];
                    for (int n; (n = entree.read(tampon)) > 0; ) { lu.write(tampon, 0, n); }
                    scriptPrise = new String(lu.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            vue.evaluateJavascript(scriptPrise, null);
            if (priseReunion) { commanderPrise(); }
        } catch (Exception e) { /* sans prise, l'interprète garde ses sous-titres */ }
    }

    /** window.rapporteurSonReunion.<fonction>(<argument>), sur le fil de la salle. */
    private void direSonReunion(String fonction, String argument) {
        toile.post(() -> {
            if (isDestroyed() || !pageAutorisee(toile.getUrl())) { return; }
            toile.evaluateJavascript("window.rapporteurSonReunion&&window.rapporteurSonReunion."
                    + fonction + "(" + argument + ")", null);
        });
    }

    /** Le seul pont du panneau : la page de réunion ne peut que DONNER le son
     *  de ses correspondants, et seulement quand la salle l'a demandé. */
    private final class PontReunion {
        @android.webkit.JavascriptInterface
        public void son(String morceau, int taux) {
            if (!priseReunion || morceau == null || morceau.isEmpty() || morceau.length() > 65536) { return; }
            if (taux < 8000 || taux > 96000 || !reunionDeConfiance(urlReunion)) { return; }
            if (!BASE64.matcher(morceau).matches()) { return; }
            direSonReunion("recevoir", "'" + morceau + "'," + taux);
        }
    }

    /**
     * Demande de Felly (15/10/2026) : la voix de l'interprète devant la voix
     * d'origine. Une page ne peut pas toucher au son d'une autre application ;
     * Android, si : tant que l'application tient le focus audio « transitoire,
     * les autres atténués », le système baisse de lui-même YouTube, la musique
     * ou une vidéo, et les rend quand on le lâche.
     */
    private AudioFocusRequest focusInterprete;

    /** Fait sur le fil du pont, AVANT de rendre la main à la page : quand elle lâche
     *  l'interprète puis ferme son micro, le volume d'appel est déjà rendu au
     *  haut-parleur (voir rendreVolumeAppel). */
    synchronized void autresSons(boolean baisser) {
        try {
            AudioManager audio = getSystemService(AudioManager.class);
            if (audio == null) { return; }
            if (baisser) {
                if (focusInterprete == null) {
                    focusInterprete = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build())
                            .setOnAudioFocusChangeListener(changement -> { /* la page garde la main */ })
                            .build();
                    audio.requestAudioFocus(focusInterprete);
                }
                suivreModeAppel(audio, true);
                monterVolumeAppel(audio);
            } else {
                suivreModeAppel(audio, false);
                rendreVolumeAppel(audio);
                if (focusInterprete != null) {
                    audio.abandonAudioFocusRequest(focusInterprete);
                    focusInterprete = null;
                }
            }
        } catch (Exception e) { /* l'interprète joue quand même */ }
    }

    /*
     * Essai de Felly (16/10/2026) : « augmenter le volume » quand l'interprète
     * écoute par le micro — une réunion sur un autre téléphone posé à côté, ou
     * la salle. La page ouvre alors son micro avec l'annulation d'écho, et le
     * WebView met le téléphone en mode « appel » : la voix de l'interprète suit
     * le VOLUME D'APPEL, souvent bas (et à zéro, le WebView coupe tout son). Tant
     * que l'interprète parle à voix haute (pas pendant un enregistrement : la page
     * ne le demande pas), ce volume monte au plus haut, au haut-parleur
     * seulement — écouteurs et casque gardent le leur —, puis revient tel
     * qu'avant, sauf si le client l'a réglé lui-même entre-temps. Android 12 et
     * suivants : l'appareil d'appel s'y lit sans erreur possible.
     */
    private int volumeAppelAvant = -1;
    private int volumeAppelPose = -1;
    private AudioManager.OnModeChangedListener ecouteModeAppel;

    private void monterVolumeAppel(AudioManager audio) {
        if (Build.VERSION.SDK_INT < 31) { return; }
        try {
            if (volumeAppelAvant >= 0 || audio.getMode() != AudioManager.MODE_IN_COMMUNICATION || !auHautParleur(audio)) { return; }
            int plus = audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            int avant = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
            if (avant >= plus) { return; }
            audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, plus, 0);
            volumeAppelAvant = avant;
            volumeAppelPose = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        } catch (Exception e) { /* le volume reste celui du client */ }
    }

    private void rendreVolumeAppel(AudioManager audio) {
        if (volumeAppelAvant < 0) { return; }
        try {
            /* Le volume se garde PAR APPAREIL : il se rend sur le haut-parleur qui
               l'a reçu, tant qu'il est l'appareil d'appel — sinon (écouteurs pris
               en route, mode « appel » déjà fini) on attend qu'il le redevienne. */
            if (!auHautParleur(audio)) { return; }
            if (audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL) == volumeAppelPose) {
                audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volumeAppelAvant, 0);
            }
            volumeAppelAvant = -1;
        } catch (Exception e) { /* rien à rendre */ }
    }

    /** Le son d'appel sort-il du haut-parleur du téléphone (ni écouteurs, ni casque) ? */
    private static boolean auHautParleur(AudioManager audio) {
        if (Build.VERSION.SDK_INT >= 31) {
            android.media.AudioDeviceInfo appareil = audio.getCommunicationDevice();
            return appareil != null && appareil.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        }
        return audio.isSpeakerphoneOn();
    }

    /** Le mode « appel » peut venir après (le micro s'ouvre une fois le partage
     *  du son arrêté) ou partir avant : on le suit tant que l'interprète parle. */
    private void suivreModeAppel(AudioManager audio, boolean suivre) {
        if (Build.VERSION.SDK_INT < 31) { return; }
        if (suivre && ecouteModeAppel == null) {
            ecouteModeAppel = mode -> {
                synchronized (ActivitePrincipale.this) {
                    AudioManager a = getSystemService(AudioManager.class);
                    if (a == null || focusInterprete == null) { return; }
                    if (mode == AudioManager.MODE_IN_COMMUNICATION) { monterVolumeAppel(a); } else { rendreVolumeAppel(a); }
                }
            };
            audio.addOnModeChangedListener(getMainExecutor(), ecouteModeAppel);
        } else if (!suivre && ecouteModeAppel != null) {
            audio.removeOnModeChangedListener(ecouteModeAppel);
            ecouteModeAppel = null;
        }
    }

    /**
     * Essai de Felly (16/10/2026) : une vidéo jouée SUR le téléphone, et
     * l'interprète restait muet — le micro efface ce que le téléphone joue
     * lui-même. Le son du téléphone lui est donc donné à la source, comme le
     * son de l'ordinateur sous Windows, avec l'accord du client (fenêtre du
     * système). Voir ServiceSonTelephone.
     */
    void sonDuTelephone(boolean capter) {
        runOnUiThread(() -> {
            if (!pageAutorisee(toile.getUrl())) { return; }
            if (!capter) {
                stopService(new Intent(this, ServiceSonTelephone.class));
                return;
            }
            if (Build.VERSION.SDK_INT < 29) { direSonTelephone("arrete", "'version'"); return; }
            try {
                android.media.projection.MediaProjectionManager projections =
                        getSystemService(android.media.projection.MediaProjectionManager.class);
                /* Tout l'écran d'emblée : le son de « une seule application »
                   ne serait pas celui que le client regarde forcément. */
                Intent demande = Build.VERSION.SDK_INT >= 34
                        ? projections.createScreenCaptureIntent(
                                android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
                        : projections.createScreenCaptureIntent();
                startActivityForResult(demande, SON_TELEPHONE);
            } catch (Exception e) {
                direSonTelephone("arrete", "'capture'");
            }
        });
    }

    /** La page qui a demandé le son, et seulement elle, le reçoit. */
    private boolean pageAutorisee(String adresse) {
        return pageDeChezNous(adresse)
                /* Banc d'essai (débogage) : la page d'essai vit dans les assets. */
                || (BuildConfig.DEBUG && adresse != null && adresse.startsWith("file:///android_asset/"));
    }

    /** window.rapporteurSonTelephone.<fonction>(<argument>), sur le fil de la page. */
    private void direSonTelephone(String fonction, String argument) {
        toile.post(() -> {
            if (isDestroyed()) { return; }
            if (!pageAutorisee(toile.getUrl())) {
                stopService(new Intent(this, ServiceSonTelephone.class));
                return;
            }
            toile.evaluateJavascript("window.rapporteurSonTelephone&&window.rapporteurSonTelephone."
                    + fonction + "(" + argument + ")", null);
        });
    }

    private final ServiceSonTelephone.Recepteur recepteurSon = new ServiceSonTelephone.Recepteur() {
        @Override
        public void pret() { direSonTelephone("pret", ""); }

        @Override
        public void recevoir(String morceau) { direSonTelephone("recevoir", "'" + morceau + "'"); }

        @Override
        public void arrete(String raison) { direSonTelephone("arrete", "'" + raison + "'"); }
    };

    private void creerPanneauReunion() {
        reunion = new WebView(this);
        WebSettings reglages = reunion.getSettings();
        reglages.setJavaScriptEnabled(true);
        reglages.setDomStorageEnabled(true);
        reglages.setMediaPlaybackRequiresUserGesture(false);
        reglages.setAllowFileAccess(false);
        reglages.setAllowContentAccess(false);
        /* Signature d'ORDINATEUR, pas de téléphone : à un téléphone, Teams
           (et Zoom) ne servent que « Télécharger l'application » — deux
           boutons qui sortiraient la réunion de Rapporteur, donc du micro
           partagé, et que ce panneau refuse : le client restait bloqué. À un
           ordinateur, ils proposent « Continuer sur ce navigateur », et la
           réunion se tient ici. Pincer pour agrandir reste possible. */
        reglages.setUserAgentString(agentOrdinateur(reglages.getUserAgentString()));
        reglages.setUseWideViewPort(true);
        reglages.setLoadWithOverviewMode(true);
        reglages.setSupportZoom(true);
        reglages.setBuiltInZoomControls(true);
        reglages.setDisplayZoomControls(false);

        reunion.addJavascriptInterface(new PontReunion(), "rapporteurReunion");
        reunion.setWebViewClient(new WebViewClient() {
            /* La prise du son, posée dès le début de chaque page puis de nouveau à
               la fin : inerte tant que la salle ne la demande pas. */
            @Override
            public void onPageStarted(WebView vue, String url, android.graphics.Bitmap favicon) {
                poserPrise(vue, url);
            }

            @Override
            public void onPageFinished(WebView vue, String url) {
                poserPrise(vue, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView vue, WebResourceRequest requete) {
                Uri url = requete.getUrl();
                String schema = url.getScheme() == null ? "" : url.getScheme();
                /* Les liens « ouvrez l'application » des services de réunion
                   ramèneraient la réunion HORS de Rapporteur — donc hors du
                   micro partagé. On reste sur la version web. */
                if (schema.equals("intent")) {
                    try {
                        Intent application = Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME);
                        String repli = application.getStringExtra("browser_fallback_url");
                        if (repli != null && repli.startsWith("https://")) { vue.loadUrl(repli); }
                    } catch (Exception e) { /* on reste où l'on est */ }
                    return true;
                }
                return !schema.equals("https") && !schema.equals("http");
            }
        });
        reunion.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest demande) {
                boolean confiance = demande.getOrigin() != null
                        && "https".equals(demande.getOrigin().getScheme())
                        && (demande.getOrigin().getPort() == -1 || demande.getOrigin().getPort() == 443)
                        && hoteDeReunion(demande.getOrigin().getHost());
                if (!confiance) { demande.deny(); return; }
                java.util.List<String> manquantes = new java.util.ArrayList<>();
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    manquantes.add(Manifest.permission.RECORD_AUDIO);
                }
                for (String ressource : demande.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(ressource)
                            && checkSelfPermission(Manifest.permission.CAMERA)
                                    != PackageManager.PERMISSION_GRANTED) {
                        manquantes.add(Manifest.permission.CAMERA);
                    }
                }
                if (manquantes.isEmpty()) {
                    demande.grant(capteursAccordes(demande));
                    return;
                }
                demandeReunion = demande;
                requestPermissions(manquantes.toArray(new String[0]), DEMANDE_REUNION);
            }
        });

        /* La barre du panneau : dire ce qui est ouvert, et le refermer. */
        TextView titre = new TextView(this);
        titre.setText(R.string.reunion_titre);
        titre.setTextColor(0xFFF0EEE6);
        titre.setPadding(dp(16), 0, 0, 0);
        titre.setGravity(Gravity.CENTER_VERTICAL);
        TextView fermer = new TextView(this);
        fermer.setText(R.string.reunion_fermer);
        fermer.setTextColor(0xFFE08D6D);
        fermer.setPadding(dp(16), 0, dp(16), 0);
        fermer.setGravity(Gravity.CENTER_VERTICAL);
        fermer.setOnClickListener((v) -> fermerReunion());
        LinearLayout barre = new LinearLayout(this);
        barre.setOrientation(LinearLayout.HORIZONTAL);
        barre.setBackgroundColor(0xFF141413);
        barre.addView(titre, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        barre.addView(fermer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        conteneurReunion = new LinearLayout(this);
        conteneurReunion.setOrientation(LinearLayout.VERTICAL);
        conteneurReunion.addView(barre, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        conteneurReunion.addView(reunion, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        colonne.addView(conteneurReunion, 0, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f));
    }

    /** L'agent que Chrome envoie depuis un ordinateur Windows, au numéro de
     *  Chrome du WebView : « … Chrome/<majeur>.0.0.0 Safari/537.36 ». */
    static String agentOrdinateur(String agentDuTelephone) {
        java.util.regex.Matcher chrome = java.util.regex.Pattern.compile("Chrome/(\\d+)")
                .matcher(agentDuTelephone == null ? "" : agentDuTelephone);
        String majeur = chrome.find() ? chrome.group(1) : "131";
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/" + majeur + ".0.0.0 Safari/537.36";
    }

    /** Les capteurs que la page de réunion a demandés ET qu'Android nous a
     *  donnés — jamais plus. La caméra refusée n'empêche pas le micro. */
    private String[] capteursAccordes(PermissionRequest demande) {
        boolean micro = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        boolean camera = checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        java.util.List<String> accordes = new java.util.ArrayList<>();
        for (String ressource : demande.getResources()) {
            if (micro && PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(ressource)) {
                accordes.add(ressource);
            }
            if (camera && PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(ressource)) {
                accordes.add(ressource);
            }
        }
        return accordes.toArray(new String[0]);
    }

    private int dp(int valeur) {
        return Math.round(valeur * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] resultats) {
        if (code == DEMANDE_MICRO && demandeEnAttente != null) {
            boolean micro = resultats.length > 0 && resultats[0] == PackageManager.PERMISSION_GRANTED;
            if (micro) {
                demandeEnAttente.grant(new String[] { PermissionRequest.RESOURCE_AUDIO_CAPTURE });
            } else {
                demandeEnAttente.deny();
                proposerLesReglages();
            }
            demandeEnAttente = null;
            return;
        }
        if (code == DEMANDE_REUNION && demandeReunion != null) {
            String[] accordes = capteursAccordes(demandeReunion);
            if (accordes.length > 0) {
                demandeReunion.grant(accordes);
            } else {
                demandeReunion.deny();
                proposerLesReglages();
            }
            demandeReunion = null;
        }
    }

    /** Micro refusé « ne plus demander » : Android n'affichera plus jamais sa
     *  fenêtre, et le client serait dans une impasse sans le savoir. On ouvre
     *  la fiche de l'application, où le micro se rend en un geste. */
    private void proposerLesReglages() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) { return; }
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) { /* pas de fiche : le message de la page guide */ }
    }

    /** Le retour du sélecteur de fichiers : un ou plusieurs, ou rien — la
     *  page attend TOUJOURS une réponse, même vide, sinon plus aucun choix
     *  de fichier ne fonctionne jusqu'au rechargement. */
    @Override
    protected void onActivityResult(int code, int resultat, Intent donnees) {
        if (code == SON_TELEPHONE) {
            /* « Commencer » : le service prend le son et le porte à la page.
               « Annuler » : l'interprète garde le micro seul. */
            if (resultat == RESULT_OK && donnees != null && pageAutorisee(toile.getUrl())) {
                ServiceSonTelephone.recepteur = recepteurSon;
                try {
                    startForegroundService(new Intent(this, ServiceSonTelephone.class)
                            .putExtra(ServiceSonTelephone.RESULTAT, resultat)
                            .putExtra(ServiceSonTelephone.DONNEES, donnees));
                } catch (Exception e) {
                    direSonTelephone("arrete", "'service'");
                }
            } else {
                direSonTelephone("arrete", "'annule'");
            }
            return;
        }
        if (code == CHOIX_FICHIERS) {
            if (attenteFichiers == null) { return; }
            Uri[] fichiers = null;
            if (resultat == RESULT_OK && donnees != null) {
                if (donnees.getClipData() != null) {
                    int n = donnees.getClipData().getItemCount();
                    fichiers = new Uri[n];
                    for (int i = 0; i < n; i++) {
                        fichiers[i] = donnees.getClipData().getItemAt(i).getUri();
                    }
                } else if (donnees.getData() != null) {
                    fichiers = new Uri[] { donnees.getData() };
                }
            }
            attenteFichiers.onReceiveValue(fichiers);
            attenteFichiers = null;
            return;
        }
        super.onActivityResult(code, resultat, donnees);
    }

    @Override
    @android.annotation.SuppressLint("GestureBackNavigation") // Android < 13 ; les versions récentes utilisent le dispatcher ci-dessus.
    public void onBackPressed() {
        revenir();
    }

    private void revenir() {
        if (conteneurReunion != null && conteneurReunion.getVisibility() == View.VISIBLE) {
            fermerReunion();
        } else if (toile != null && toile.canGoBack()) {
            toile.goBack();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        /* La session du client survit à la fermeture : même principe que la
           partition persistante de l'application Windows. */
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        /* L'activité meurt pour de bon : plus personne ne grave. Le service
           ne doit pas survivre à la page qui tenait le micro. */
        if (isFinishing()) { stopService(new Intent(this, ServiceEnregistrement.class)); }
        /* Le son du téléphone n'a plus de page à qui parler. */
        ServiceSonTelephone.recepteur = null;
        stopService(new Intent(this, ServiceSonTelephone.class));
        /* L'interprète se tait avec la page (son micro est encore ouvert ici) :
           volume d'appel et autres sons rendus. */
        autresSons(false);
        if (reunion != null) { reunion.destroy(); }
        toile.destroy();
        super.onDestroy();
    }
}
