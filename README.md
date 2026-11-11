# Neon

Lecteur de musique Android (Kotlin, Jetpack Compose, Media3, Room). Thème noir avec contours bleu-violet.

## Obtenir l'APK

### Option A — GitHub Actions (rien à installer)
1. Créez un dépôt GitHub (privé ou public) et envoyez-y tout le contenu de ce dossier (branche `main`).
2. Onglet **Actions** > workflow **Build APK** (il se lance seul au push, ou via *Run workflow*).
3. Une fois terminé, téléchargez l'artefact **NeonPlayer-apk** : il contient `app-debug.apk`.
4. Copiez l'APK sur le téléphone et installez-le (autoriser les sources inconnues).

### Option B — Android Studio
1. Ouvrez ce dossier dans Android Studio (Koala ou plus récent) et laissez la synchronisation Gradle se terminer.
2. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
3. L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`.

## Notes
- Supprimer un titre le retire de l'application, jamais du téléphone.
- Pour de grandes collections, préférez « Ajouter un dossier » : Android limite le nombre de fichiers individuels mémorisables.
- Les sauvegardes (Profil) contiennent la bibliothèque et les images, pas les fichiers audio.

## Mise à jour
Ce projet est signé avec une clé fixe (`app/debug.keystore`) : les prochaines versions s'installeront par-dessus l'ancienne sans rien perdre.
La toute première installation de la v1.1 demande en revanche de désinstaller l'ancienne version (clé différente) : exportez d'abord une sauvegarde depuis Profil.

## Nouveautés 1.2
Logo, export M4A (vitesse/hauteur), sélection multiple, tris, boucle A-B, actualisation des dossiers,
statistiques par période, thèmes personnalisés, partage de fichier, fondu entre les titres.
