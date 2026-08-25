package com.lerapporteur.mobile;

import android.webkit.JavascriptInterface;

/**
 * Le seul pont entre la page et l'application — l'équivalent du preload de
 * l'application Windows.
 *
 * Le site sait ainsi qu'il tourne dans l'application Android — pour adapter la
 * salle d'enregistrement (l'écran peut s'éteindre), marquer les envois
 * « mobile » auprès du serveur, et tenir le service de premier plan pendant la
 * capture. Rien d'autre ne passe : pas d'accès au système depuis la page.
 */
public class PontRapporteur {

    private final ActivitePrincipale hote;

    PontRapporteur(ActivitePrincipale hote) {
        this.hote = hote;
    }

    @JavascriptInterface
    public String version() {
        return BuildConfig.VERSION_NAME;
    }

    @JavascriptInterface
    public String plateforme() {
        return "android";
    }

    /** La page lance la capture : le service tient le micro, écran éteint compris. */
    @JavascriptInterface
    public void enregistrementDemarre() {
        hote.demarrerService();
    }

    /** La capture est finie : le service n'a plus de raison de tourner. */
    @JavascriptInterface
    public void enregistrementTermine() {
        hote.arreterService();
    }
}
