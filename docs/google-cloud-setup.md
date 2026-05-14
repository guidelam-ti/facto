# Setup Google Cloud — facto

Ce guide décrit les manipulations **manuelles** à effectuer dans la console Google Cloud avant de pouvoir utiliser l'authentification OAuth 2.0 de l'application `facto`.

À l'issue de ce setup, tu disposeras d'un `client_id` et d'un `client_secret` à saisir dans l'écran `/setup` de l'application.

> ℹ️ **Note UI** : ce guide reflète l'interface Google Cloud Console à jour au **T2 2026**. Depuis 2025, l'écran de consentement OAuth est éclaté en plusieurs sections (Branding, Audience, Data Access, Clients) sous le produit **Google Auth Platform**. Si l'UI a encore évolué depuis, les concepts restent les mêmes.

---

## Prérequis

- Un compte Google (Gmail perso suffit — pas besoin de Workspace)
- ~10-15 minutes
- Aucune carte bancaire requise (les APIs Gmail/Drive sont gratuites dans les quotas standards)

---

## Étape 1 — Créer le projet Google Cloud

1. Aller sur https://console.cloud.google.com et se connecter avec le compte Google qui possède la boîte Gmail à archiver.
2. En haut à gauche, cliquer sur le **sélecteur de projet** (à côté du logo « Google Cloud »).
3. Cliquer sur **« NOUVEAU PROJET »** en haut à droite de la fenêtre.
4. Renseigner :
   - **Nom du projet** : `facto-invoice-archiver` (ou autre nom parlant)
   - **Organisation** : laisser « Aucune organisation » pour un compte Gmail perso
5. Cliquer **« CRÉER »** et attendre la confirmation (~10-20 sec).
6. **Sélectionner le projet créé** dans le sélecteur en haut.

> ⚠️ Toutes les étapes suivantes doivent être réalisées **dans ce projet**. Vérifier régulièrement le sélecteur en haut.

---

## Étape 2 — Activer les APIs nécessaires

`facto` utilise deux APIs Google : Gmail (lecture des emails contenant des factures) et Drive (upload des PDFs archivés).

1. Menu ☰ → **APIs et services** → **Bibliothèque**.
2. Rechercher `Gmail API` → cliquer sur le résultat → **ACTIVER**.
3. Retourner à la **Bibliothèque** (menu de gauche).
4. Rechercher `Google Drive API` → cliquer sur le résultat → **ACTIVER**.

**Vérification :** menu ☰ → **APIs et services** → **APIs et services activés**. La liste doit contenir `Gmail API` et `Google Drive API`.

---

## Étape 3 — Configurer Google Auth Platform

### 3.1 Initialiser

1. Menu ☰ → **APIs et services** → **Écran de consentement OAuth**.
2. Sur la page « Google Auth Platform pas encore configuré », cliquer **« Premiers pas »**.

### 3.2 App Information (Branding)

| Champ | Valeur |
|---|---|
| Nom de l'application | `facto` |
| E-mail d'assistance utilisateur | _ton email Google_ |

→ **Suivant**

### 3.3 Audience

- Type d'utilisateur : **Externe**

> Sur un compte Gmail perso, « Interne » n'est pas disponible. « Externe » + mode Test est le bon choix pour un usage personnel.

→ **Suivant**

### 3.4 Contact information

- Adresse e-mail : _ton email_

→ **Suivant**

### 3.5 Finish

- Cocher l'acceptation des Règles relatives aux données utilisateur des services d'API Google.

→ **Continuer** → **Créer**

### 3.6 Ajouter les scopes (Data Access)

1. Dans le menu de gauche de Google Auth Platform, cliquer **« Accès aux données »** (Data Access).
2. Cliquer **« AJOUTER OU SUPPRIMER DES NIVEAUX D'ACCÈS »**.
3. Dans le panneau de droite, cocher exactement ces trois scopes :

| Scope | Usage |
|---|---|
| `https://www.googleapis.com/auth/gmail.readonly` | Lecture seule des emails (Gmail) |
| `https://www.googleapis.com/auth/drive.metadata.readonly` | Lister les dossiers Drive existants pour le choix du dossier racine d'archivage |
| `https://www.googleapis.com/auth/drive.file` | Créer/écrire/lire les fichiers créés par l'app (Drive) |

> 💡 Astuce : utiliser le filtre du panneau (`gmail.readonly`, `drive.metadata.readonly`, puis `drive.file`) pour trouver rapidement.

4. Cliquer **« METTRE À JOUR »** dans le panneau, puis **« ENREGISTRER »** sur la page.

> ℹ️ `gmail.readonly` est classé comme « scope restreint » par Google. Aucun impact tant que l'app reste en **mode Test**. Une vérification Google ne serait nécessaire que pour passer en Production publique.

### 3.7 Ajouter un test user (Audience)

En mode Test, seuls les emails listés ici peuvent s'authentifier (limite : 100 utilisateurs).

1. Menu de gauche → **Audience**.
2. Section **Utilisateurs tests** → **+ ADD USERS**.
3. Saisir l'email Google qui contient les factures à archiver.
4. **ENREGISTRER**.

---

## Étape 4 — Créer les credentials OAuth 2.0

1. Menu de gauche de Google Auth Platform → **Clients**.
2. Cliquer **« + CRÉER UN CLIENT »**.
3. Remplir :
   - **Type d'application** : `Application Web`
   - **Nom** : `facto-local` (ou tout libellé parlant)
4. Section **« URI de redirection autorisés »** → **« + AJOUTER UN URI »** :

```
http://localhost:8080/oauth/callback
```

> ⚠️ Vérifier exactement : `http` (pas `https`), port `8080`, chemin `/oauth/callback`, sans slash final. Toute différence cassera le flow OAuth.

5. Laisser « Origines JavaScript autorisées » vide.
6. Cliquer **« CRÉER »**.
7. Une popup affiche :
   - **ID client** (format : `xxxxxxxxxxxx-xxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com`)
   - **Code secret du client** (format : `GOCSPX-xxxxxxxxxxxxxxx`)
8. **Sauvegarder ces deux valeurs** (gestionnaire de mots de passe, ou bouton « TÉLÉCHARGER LE FICHIER JSON »).

---

## Étape 5 — Saisir les credentials dans `facto`

1. Lancer l'application : `./mvnw spring-boot:run`
2. Ouvrir http://localhost:8080/setup
3. Saisir le `client_id` et le `client_secret` obtenus à l'étape 4.
4. Soumettre le formulaire.
5. Cliquer sur « Connecter Google » : tu seras redirigé vers l'écran de consentement Google → accepter → retour automatique sur `facto`.

---

## 🔒 Sécurité

- **Ne jamais committer** `client_id` ni `client_secret` dans Git.
- Ces valeurs sont persistées dans la table `app_setting` de PostgreSQL local. Le mot de passe de la base lui-même est stocké en clair dans `C:\facto\config\application.properties` (NTFS restreint à l'utilisateur) — voir l'avertissement sécurité du `README.md`.
- En cas de fuite du `client_secret` : régénérer immédiatement via Google Auth Platform → **Clients** → ouvrir le client → **« RÉINITIALISER LE SECRET »**.
- L'app reste en mode **Testing** indéfiniment pour usage perso : aucun besoin de soumettre à la vérification Google.

---

## 🛠 Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| `Error 400: redirect_uri_mismatch` au callback | URI dans le code ≠ URI déclaré dans la console | Vérifier `http://localhost:8080/oauth/callback` à l'identique côté Google + côté app |
| `Error 403: access_denied` après consentement | Compte non listé en test user | Ajouter l'email dans **Audience → Utilisateurs tests** |
| `invalid_grant` au refresh | Token révoqué ou compte mis en pause | Re-déclencher le flow OAuth depuis `/setup` |
| Pas de `refresh_token` reçu | Premier consentement déjà accordé sans `prompt=consent` | Révoquer l'app dans https://myaccount.google.com/permissions puis recommencer |
| API call → `403 PERMISSION_DENIED` | API non activée sur le projet | Re-vérifier l'étape 2 |

---

## Récapitulatif des artefacts créés côté Google

- ✅ Projet : `facto-invoice-archiver`
- ✅ APIs activées : Gmail API, Google Drive API
- ✅ Google Auth Platform : configuré (Externe, Test)
- ✅ Scopes : `gmail.readonly`, `drive.metadata.readonly`, `drive.file`
- ✅ Test user : email du propriétaire
- ✅ Client OAuth 2.0 Web : redirect URI `http://localhost:8080/oauth/callback`
- ✅ `client_id` + `client_secret` : sauvegardés en lieu sûr
