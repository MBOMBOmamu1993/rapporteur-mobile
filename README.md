# Rapporteur pour Android

La coquille mobile de [lerapporteur.com](https://lerapporteur.com) — le pendant
Android de l'application Windows (`rapporteur-bureau`), avec la même
philosophie : **aucune logique du service dans l'application**. Elle charge le
site et lui ajoute la seule chose qu'un navigateur de téléphone ne sait pas
faire — continuer d'enregistrer **écran éteint**, grâce à un service de premier
plan « microphone ».

## Architecture

| Pièce | Rôle |
|---|---|
| `ActivitePrincipale` | Un WebView plein écran qui charge `lerapporteur.com/mobile`. Liens externes → vrai navigateur ; caisses de paiement (Stripe, CinetPay) → dans l'application, pour garder la session. |
| `PontRapporteur` | Le seul pont page ↔ application (`window.rapporteurAndroid`) : `version()`, `plateforme()`, `enregistrementDemarre()`, `enregistrementTermine()`. L'équivalent du preload Electron. |
| `ServiceEnregistrement` | Service de premier plan type `microphone` + verrou processeur (8 h max). Il ne capte RIEN lui-même : la capture vit dans la page, comme au navigateur. |

Côté site (`rapporteur-web`) : `/mobile` est l'accueil de l'application (un
bouton micro au centre), la salle `/enregistrer` reconnaît le pont
(`DANS_ANDROID`) pour adapter ses textes et démarrer/arrêter le service, et les
envois partent marqués `origine: "mobile"` — soumis aux règles du site,
appliquées PAR LE SERVEUR.

## Sécurité — ce qui est verrouillé, et pourquoi

- **Rien à voler dans l'application** : pas de clés, pas de prompts, pas de
  logique métier. La session est le cookie du site ; la sécurité est celle des
  routes API, côté serveur.
- **Le micro n'est accordé qu'à `https://lerapporteur.com`**
  (`onPermissionRequest` vérifie l'origine) : une page tierce affichée dans le
  WebView (caisse de paiement) n'obtient rien.
- **Le service ne démarre que depuis nos pages** : le pont vérifie l'adresse
  courante du WebView avant `startForegroundService`.
- **Pas d'accès disque depuis la page** (`setAllowFileAccess(false)`,
  `setAllowContentAccess(false)`), pas de trafic en clair
  (`usesCleartextTraffic="false"`), service non exporté.
- **Tout site étranger s'ouvre dans le vrai navigateur** : l'application ne
  montre que lerapporteur.com (et les caisses de paiement, qui doivent revenir
  avec la session).
- **La clé de signature vit HORS du dépôt** : `cle-envoi.jks` et
  `keystore.properties` sont ignorés par git. **SAUVEGARDEZ-LES** (le mot de
  passe est dans `keystore.properties`). Avec Play App Signing, cette clé n'est
  que la clé D'ENVOI : Google peut la remplacer si elle se perd — l'APK direct,
  lui, dépend d'elle pour ses mises à jour.

## Construire

Prérequis : Android SDK (installé avec Android Studio) et le JDK d'Android
Studio. Depuis la racine :

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat bundleRelease assembleRelease
```

- **AAB pour Google Play** : `app/build/outputs/bundle/release/app-release.aab`
- **APK pour le téléchargement direct** : `app/build/outputs/apk/release/app-release.apk`
  → renommé `Rapporteur.apk` et publié en GitHub Release sur ce dépôt ; le site
  pointe `releases/latest/download/Rapporteur.apk`, comme pour Windows.

À chaque nouvelle version : augmenter `versionCode` (+1) et `versionName` dans
`app/build.gradle.kts`, reconstruire, publier l'AAB sur Play ET l'APK en
Release GitHub.

## Publier sur Google Play — les étapes

1. **Compte développeur** : [play.google.com/console](https://play.google.com/console),
   inscription unique 25 $ (compte Google : fellybokota@gmail.com).
2. **Créer l'application** : nom « Rapporteur », langue par défaut
   français (France), application, gratuite.
3. **Fiche du magasin** : description courte/longue (FR + EN), icône 512×512,
   bannière 1024×500, au moins 2 captures d'écran de téléphone (prendre
   /mobile et /enregistrer dans l'application).
4. **Politique de confidentialité** : `https://lerapporteur.com/confidentialite`.
5. **Sécurité des données** (Data Safety) : déclarer la collecte
   « Enregistrements audio » (fonctionnalité de l'application, chiffrés en
   transit, l'utilisateur demande lui-même l'enregistrement), adresse
   courriel (gestion de compte). Pas de partage à des tiers à des fins
   publicitaires.
6. **Autorisation sensible** : le service de premier plan `microphone` exige
   une déclaration en console (motif : enregistrement de réunions demandé par
   l'utilisateur, l'écran pouvant s'éteindre pendant la séance) et une courte
   **vidéo de démonstration** montrant le parcours : bouton Enregistrer →
   notification visible → écran éteint → enregistrement toujours en cours.
7. **Version** : Production → créer une version → téléverser
   `app-release.aab` → notes de version FR/EN → envoyer en validation
   (quelques jours la première fois).
8. Une fois validée, le lien du site devient actif :
   `https://play.google.com/store/apps/details?id=com.lerapporteur.mobile`.

## Ce que l'application NE fait PAS (voulu)

- Pas de capture du son des autres applications (Teams/Zoom mobiles) : Android
  ne le permet pas aux applications ordinaires. Le mode téléphone du site —
  réunion sur haut-parleur, micro qui capte tout — est le bon parcours, et les
  voix sont départagées sur nos serveurs.
- Pas de mise à jour intégrée : Google Play s'en charge ; l'APK direct suit
  `releases/latest`, comme Windows.
