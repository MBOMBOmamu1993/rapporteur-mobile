# Fiche Google Play — Rapporteur

Textes prêts à coller dans Play Console → Présence sur le Play Store →
Fiche du Play Store principale.

## Identité

- **Nom de l'application** (30 car. max) : `Rapporteur — Comptes rendus`
- **Identifiant du paquet** : `com.lerapporteur.mobile` (fixé par l'AAB, définitif)
- **Catégorie** : Productivité
- **Adresse de contact** : info@lerapporteur.com
- **Politique de confidentialité** : https://lerapporteur.com/confidentialite

## Description courte (80 car. max) — version 1.1.6 (21/09/2026)

FR : `Vos réunions, déjà rédigées : enregistrez, le compte rendu arrive par courriel.`

EN : `Your meetings, already written: record, the report arrives by email.`

## Description complète (4000 car. max) — version 1.1.6

Règle (retour d'Apple 2.3.10, appliquée ici par symétrie) : la fiche Google Play
ne nomme AUCUNE autre plateforme — ni iPhone, ni App Store, ni Windows, ni Mac,
ni « ordinateur ». Uniquement l'application Android.

FR :

```
Rapporteur transforme vos réunions en comptes rendus professionnels, sans que
vous ayez à prendre une seule note.

COMMENT ÇA MARCHE
• Touchez le bouton micro au début de la réunion.
• Posez le téléphone au centre de la table : l'enregistrement continue écran éteint.
• Touchez « Terminer » : l'audio part automatiquement.
• Quelques minutes plus tard, recevez un compte rendu structuré en Word :
  participants, points discutés, décisions, actions et responsables.

TROIS FAÇONS D'ENREGISTRER
• En salle : la réunion se tient autour de vous, le micro capte toute la salle.
• En ligne : collez le lien de la réunion (Meet, Teams, Zoom…), elle s'ouvre
  dans Rapporteur et tout est capté — ou posez le téléphone près d'un autre
  appareil à haut-parleur.
• Relecture : rejouez l'enregistrement d'une réunion passée, il est rédigé
  comme une séance en direct.

FIDÈLE AUX FAITS
Le compte rendu s'appuie uniquement sur ce qui a été dit. Pas d'invention,
pas d'approximation : les chiffres, les noms et les décisions sont restitués
tels quels.

PENSÉ POUR LE TERRAIN
• L'écran peut s'éteindre : l'enregistrement continue toute la séance.
• Sans réseau, les réunions attendent sur le téléphone et partent toutes
  seules quand la connexion revient.
• Réunions longues acceptées (plusieurs heures).
• Un avertisseur vous prévient si plus rien n'est capté.
• Interface en français, anglais, portugais et espagnol ; réunions tenues en
  français, en anglais et dans plusieurs autres langues parlées.
• Vos enregistrements sont transmis chiffrés et traités sur nos serveurs ;
  ils sont supprimés après la rédaction.

ESSAI GRATUIT
Trois comptes rendus offerts à l'inscription, avec toutes les fonctionnalités.
```

EN :

```
Rapporteur turns your meetings into professional meeting reports, without you
taking a single note.

HOW IT WORKS
• Tap the microphone button when the meeting starts.
• Set the phone down in the middle of the table: recording carries on with the
  screen off.
• Tap "Finish": the audio is sent automatically.
• A few minutes later, receive a structured Word report: participants, points
  discussed, decisions, actions and owners.

THREE WAYS TO RECORD
• In the room: the meeting is held around you, the microphone picks up the
  whole room.
• Online: paste the meeting link (Meet, Teams, Zoom…), it opens inside
  Rapporteur and everything is captured — or set the phone next to another
  device on speaker.
• Playback: replay the recording of a past meeting, it is written up like a
  live session.

FAITHFUL TO THE FACTS
The report relies only on what was said. Nothing invented, nothing
approximated: figures, names and decisions are rendered as they were.

BUILT FOR THE FIELD
• The screen can turn off: recording carries on for the whole session.
• Without network, meetings wait on the phone and send themselves when the
  connection returns.
• Long meetings accepted (several hours).
• An alert warns you if nothing is being captured any more.
• Interface in French, English, Portuguese and Spanish; meetings held in
  French, English and several other spoken languages.
• Your recordings travel encrypted and are processed on our servers; they are
  deleted once the report is written.

FREE TRIAL
Three meeting reports on us when you sign up, every feature included.
```

## Notes de version 1.1.6 (500 car. max par langue)

FR :

```
• Toutes les pages s'ouvrent hors connexion après une première ouverture en ligne ; les réunions attendent sur le téléphone et partent seules au retour du réseau.
• Interface en français, anglais, portugais et espagnol.
• Avertisseur de silence et rappel périodique pendant la séance.
• Corrections issues des retours des testeurs : fin d'envoi sur téléphone, nombres restitués tels qu'entendus, en-tête sur une ligne.
• Capture et sauvegardes durcies.
```

EN :

```
• Every page opens offline after a first online launch; meetings wait on the phone and send themselves when the network returns.
• Interface in French, English, Portuguese and Spanish.
• Silence alert with periodic reminder during the session.
• Fixes from tester feedback: end-of-upload wording on phones, numbers rendered as heard, single-line header.
• Hardened capture and backups.
```

## Visuels 1.1.6

`play-store/visuels/1.1.6/{fr,en}/` — engendrés par `meetingrec-web/outils/demo/captures-play.mjs`
(5 captures 1080 × 1920 + `presentation.png` 1024 × 500) et `apercu-play.mjs`
(`video-play.mp4` 1080 × 1920, voix + musique, à téléverser sur YouTube non
répertorié puis à lier dans la fiche).

## Éléments graphiques à fournir

| Élément | Format | État |
|---|---|---|
| Icône | 512×512 PNG, 32 bits | à exporter depuis `ic_launcher` (fond + premier plan) |
| Image de présentation (feature graphic) | 1024×500 PNG/JPG | à créer (fond sombre + logo + « Le compte rendu, sans effort ») |
| Captures téléphone | min. 2, ratio 9:16, min. 1080 px | partir de `meetingrec-web/docs/app-android-accueil.png` + captures émulateur (salle d'enregistrement, compte rendu reçu) |
| Vidéo promo | lien YouTube | scénario dans `scenario-video-playstore.md` |

## Déclarations « Contenu de l'application » (Play Console)

| Rubrique | Réponse |
|---|---|
| Politique de confidentialité | https://lerapporteur.com/confidentialite |
| Annonces | Non, l'application ne contient pas de publicité |
| Accès à l'application | Tout est accessible **avec un compte** → fournir un identifiant de démonstration (créer un compte d'essai dédié pour l'examen Google) |
| Classification du contenu | Questionnaire → catégorie « Utilitaire / productivité », aucun contenu sensible |
| Public cible | 18 ans et plus (outil professionnel, pas d'attrait pour les enfants) |
| Sécurité des données | Collecte : **audio** (enregistrements de réunions), **adresse e-mail** (compte). Traitement : transmis chiffré (HTTPS), utilisé pour produire le compte rendu, non partagé à des tiers, suppression sur demande. Audio conservé selon la règle `travaux/audio_conserve`. |
| Services au premier plan | Type **microphone** — description + vidéo : voir `scenario-video-playstore.md`, vidéo 2 |
| Application gouvernementale / actualités / COVID | Non |

## ⚠️ Risque de conformité à trancher AVANT l'examen

L'application ouvre les caisses **Stripe et CinetPay dans le WebView** pour
vendre un service numérique (abonnements/codes). La politique Google Play
« Paiements » exige la facturation Google Play pour les biens numériques
consommés dans l'application, et interdit d'y rediriger vers un paiement
externe. Deux options :

1. **Sans risque (recommandé au lancement)** : quand `DANS_ANDROID` est vrai,
   masquer prix/boutons d'achat dans l'app (modèle « consommation seule »,
   comme Netflix) — on achète sur le site au navigateur.
2. **Risqué** : laisser tel quel et espérer passer l'examen — exposition à un
   rejet ou à un retrait ultérieur.

L'APK distribué en direct sur le site, lui, n'est pas concerné et peut garder
les caisses intégrées.
