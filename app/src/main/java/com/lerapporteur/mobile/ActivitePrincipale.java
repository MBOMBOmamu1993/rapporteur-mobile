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
    /* Le défi de la connexion en cours : armé au départ vers le navigateur,
       exigé par le serveur à l'échange du billet. Une application tierce qui
       écouterait rapporteur:// aurait le billet, jamais ce défi. */
    private String defi;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);

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
                    return false;
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
            public void onReceivedError(WebView vue, WebResourceRequest requete, WebResourceError erreur) {
                /* Hors connexion, un écran local remplace la page — comme le
                   fait l'accueil de secours de l'application Windows. */
                if (requete.isForMainFrame()) {
                    vue.loadUrl("file:///android_asset/hors-ligne.html");
                }
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
                        && SITE.equals(demande.getOrigin().toString().replaceAll("/$", ""));
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
            String adresse = toile.getUrl();
            if (adresse == null || !adresse.startsWith(SITE)) { return; }
            startForegroundService(new Intent(this, ServiceEnregistrement.class));
        });
    }

    void arreterService() {
        runOnUiThread(() -> stopService(new Intent(this, ServiceEnregistrement.class)));
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
                String depuis = toile.getUrl();
                if (depuis == null || !depuis.startsWith(SITE)) { return; }
                Uri lien = Uri.parse(brut == null ? "" : brut.trim());
                if (!"https".equals(lien.getScheme())) { return; }
                if (reunion == null) { creerPanneauReunion(); }
                conteneurReunion.setVisibility(View.VISIBLE);
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
                reunion.loadUrl("about:blank");
                conteneurReunion.setVisibility(View.GONE);
            } catch (Exception e) { /* même règle : la salle avant tout */ }
        });
    }

    private void creerPanneauReunion() {
        reunion = new WebView(this);
        WebSettings reglages = reunion.getSettings();
        reglages.setJavaScriptEnabled(true);
        reglages.setDomStorageEnabled(true);
        reglages.setMediaPlaybackRequiresUserGesture(false);
        reglages.setAllowFileAccess(false);
        reglages.setAllowContentAccess(false);
        /* Même signature que la salle : les services de réunion servent leur
           version web complète au Chrome du téléphone. */
        reglages.setUserAgentString(reglages.getUserAgentString()
                .replace("; wv", "").replaceFirst("Version/\\d+\\.\\d+ ", ""));

        reunion.setWebViewClient(new WebViewClient() {
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

    @Override
    public void onBackPressed() {
        if (toile.canGoBack()) { toile.goBack(); } else { super.onBackPressed(); }
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
        if (reunion != null) { reunion.destroy(); }
        toile.destroy();
        super.onDestroy();
    }
}
