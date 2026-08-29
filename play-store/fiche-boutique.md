# Fiche Google Play — Rapporteur

Textes prêts à coller dans Play Console → Présence sur le Play Store →
Fiche du Play Store principale.

## Identité

- **Nom de l'application** (30 car. max) : `Rapporteur — Comptes rendus`
- **Identifiant du paquet** : `com.lerapporteur.mobile` (fixé par l'AAB, définitif)
- **Catégorie** : Productivité
- **Adresse de contact** : info@lerapporteur.com
- **Politique de confidentialité** : https://lerapporteur.com/confidentialite

## Description courte (80 car. max)

FR : `Enregistrez vos réunions, recevez un compte rendu rédigé par l'IA.`

EN : `Record your meetings, get an AI-written meeting report in minutes.`

## Description complète (4000 car. max)

FR :

```
Rapporteur transforme vos réunions en comptes rendus professionnels, sans que
vous ayez à prendre une seule note.

COMMENT ÇA MARCHE
• Touchez le bouton micro au début de la réunion.
• Rangez le téléphone : l'enregistrement continue écran éteint.
• Touchez « Terminer » : l'audio part automatiquement.
• Quelques minutes plus tard, recevez un compte rendu structuré : participants,
  points discutés, décisions, actions et responsables.

FIDÈLE AUX FAITS
Le compte rendu s'appuie uniquement sur ce qui a été dit. Pas d'invention,
pas d'approximation : les chiffres, les noms et les décisions sont restitués
tels quels.

PENSÉ POUR LE TERRAIN
• Fonctionne en français et en anglais.
• Reprend l'envoi automatiquement si le réseau coupe.
• Réunions longues acceptées (plusieurs heures).
• Vos enregistrements sont transmis en toute sécurité (HTTPS) et traités sur
  nos serveurs ; rien n'est partagé avec des tiers.

ESSAI GRATUIT
Trois comptes rendus offerts à l'inscription, avec toutes les fonctionnalités.

Rapporteur existe aussi sur Windows et directement au navigateur :
https://lerapporteur.com
```

EN : reprendre la version anglaise du site (`/en`), glossaire « meeting
report » — ne jamais dire « minutes » seul.

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
