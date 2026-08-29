# Scénarios vidéo — Google Play

Deux vidéos à produire. La première est la vidéo promotionnelle de la fiche
Play Store (lien YouTube, paysage 16:9, 30–60 s). La seconde est la **vidéo de
démonstration obligatoire** que Google exige dans la déclaration du service de
premier plan « microphone » (elle peut être non répertoriée sur YouTube).

---

## Vidéo 1 — Promo fiche Play Store (≈ 50 s, 16:9, 1920×1080)

Style : même famille que la pub de 50 s déjà montée (`Downloads\rapporteur-pub-assets`),
mais centrée sur le téléphone. Téléphone cadré dans un gabarit (device frame),
fond uni sombre, texte à gauche, téléphone à droite.

Captation : émulateur Android (ou téléphone réel via `scrcpy --record`), puis
montage ffmpeg comme `monter.sh`. Voix : même pipeline que `outils/demo/tourner.mjs`.

| # | Durée | Image (téléphone) | Voix off (FR) | Texte à l'écran |
|---|-------|-------------------|----------------|------------------|
| 1 | 0–5 s | Logo Rapporteur, puis l'accueil `/mobile` : le gros bouton micro | « Vos réunions méritent mieux que des notes prises à la volée. » | **Rapporteur** — Le compte rendu, sans effort |
| 2 | 5–12 s | Doigt qui touche le bouton micro, l'enregistrement démarre, le chrono tourne | « Une touche, et Rapporteur enregistre votre réunion. » | Enregistrez d'une touche |
| 3 | 12–20 s | L'écran du téléphone s'éteint (fondu noir avec la notification visible dans la barre), le chrono continue | « Écran éteint, dans votre poche : l'enregistrement continue. » | Continue écran éteint 🔒 |
| 4 | 20–30 s | Fin de réunion, touche « Terminer », animation d'envoi | « À la fin, tout part automatiquement. L'intelligence artificielle rédige. » | L'IA rédige pour vous |
| 5 | 30–42 s | Le compte rendu reçu : document propre qui défile (participants, décisions, actions) | « Quelques minutes plus tard : un compte rendu fidèle, structuré, prêt à partager. Décisions, actions, responsables. » | Fidèle. Structuré. Prêt à envoyer. |
| 6 | 42–50 s | Retour au logo + adresse | « Rapporteur. Enregistrez. Recevez. C'est tout. Essai gratuit : trois comptes rendus. » | **lerapporteur.com** — 3 comptes rendus offerts |

Notes de tournage :
- Réutiliser les plans du film d'accueil (`outils/demo/tourner.mjs`) pour le
  plan 5 (compte rendu qui défile) — masquer `.cpt-annonce`.
- Le plan 3 (écran éteint) : filmer la barre de notification avec
  « Rapporteur — enregistrement en cours », puis fondu au noir avec seulement
  le chrono incrusté. Pas besoin de filmer un vrai téléphone éteint.
- Version EN : mêmes plans sur `/en/mobile`, glossaire « meeting report »
  (mémoire : jamais « minutes » seul). Penser `localStorage rapporteur:langue`
  avant captation.
- Musique libre de droits discrète ; la voix prime.
- Publier sur YouTube en **public ou non répertorié, sans publicité**, et
  coller l'URL dans la fiche Play Store.

---

## Vidéo 2 — Démonstration du service de premier plan micro (obligatoire, ≈ 40 s)

Google demande : « les étapes que l'utilisateur suit dans l'application pour
déclencher la fonctionnalité ». Screencast brut, sans montage ni musique,
YouTube non répertorié. À coller dans Play Console → Contenu de l'application
→ Autorisations de service au premier plan → type **microphone**.

| # | Ce qu'on montre |
|---|-----------------|
| 1 | Lancement de l'application (icône → accueil `/mobile`) |
| 2 | Touche sur le bouton micro → Android demande l'autorisation micro → « Autoriser » |
| 3 | L'enregistrement démarre ; tirer la barre de notification : la notification persistante « Rapporteur — enregistrement en cours » est visible (c'est LE service de premier plan) |
| 4 | Appuyer sur Accueil (l'app passe en arrière-plan) : la notification reste, le chrono continue |
| 5 | Revenir dans l'app, toucher « Terminer » : la notification disparaît (le service s'arrête) |

Captation en une prise : `adb shell screenrecord` ou scrcpy sur l'émulateur
déjà utilisé pour les essais de la v1.0.1.

Texte de déclaration à joindre (champ « description de la fonctionnalité ») :

> L'application enregistre l'audio des réunions à la demande explicite de
> l'utilisateur (bouton micro). Le service de premier plan de type
> « microphone » maintient l'enregistrement lorsque l'écran s'éteint ou que
> l'application passe en arrière-plan, avec une notification persistante.
> Le service démarre uniquement depuis l'application et s'arrête quand
> l'utilisateur termine l'enregistrement.
