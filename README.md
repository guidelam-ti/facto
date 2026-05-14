# facto

Application web **locale** (lancée via `java -jar facto.jar`, accessible sur `http://localhost:8080`) qui automatise la récupération, l'organisation et l'archivage de factures reçues par courriel.

- **Source** : Gmail (compte personnel)
- **Destination** : Google Drive (compte personnel)

## Stack

- Java 21 (LTS)
- Spring Boot 4.0.x — Spring Web, Thymeleaf, Spring Data JPA, validation
- Thymeleaf 3 + Layout Dialect
- Bootstrap 5.3 + Bootstrap Icons + police Inter (servis localement, pas de CDN)
- PostgreSQL (service local Windows par défaut ; URL/creds surchargeables via env vars ou config externe)
- Pas de Spring Security (app loopback uniquement)

## Setup initial

**Obligatoire avant le premier lancement de l'app**, à faire une seule fois : créer un projet Google Cloud, activer les API Gmail et Drive, configurer l'écran de consentement OAuth, créer un client OAuth de type *Web application* et récupérer son `client_id` / `client_secret`.

La procédure complète, pas-à-pas, avec dépannage, est dans **[docs/google-cloud-setup.md](docs/google-cloud-setup.md)**.

À la fin de ce setup tu auras un `client_id` et un `client_secret` à coller dans l'écran `/setup` de l'app au premier démarrage.

## Démarrage rapide

Prérequis :
- **Java 21** (JDK 17 ne fonctionne pas avec Spring Boot 4)
- **PostgreSQL** installé localement avec une base `facto` :
  ```sh
  psql -U postgres -c "CREATE DATABASE facto"
  ```
- Le [setup initial](#setup-initial) Google Cloud terminé

```sh
# Build
./mvnw clean package

# Run (les defaults pointent sur jdbc:postgresql://localhost:5432/facto, user postgres).
# Surcharger via variables d'env si besoin :
#   SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
SPRING_DATASOURCE_PASSWORD=xxx java -jar target/facto-0.1.0-SNAPSHOT.jar

# Open
http://localhost:8080
```

Pour inspecter la base : `psql -U postgres -d facto` ou via pgAdmin.

## État du projet

Le projet est livré par **étapes A → G** :

| Étape | Sujet | État |
|------|------|------|
| A | Squelette projet (build, layout, page d'accueil) | **livré** |
| B | Modèle de données JPA + repositories | à venir |
| C | OAuth 2.0 Google (Gmail + Drive) | à venir |
| D | Choix du dossier Drive racine | à venir |
| E | Scan Gmail + validation des fournisseurs | à venir |
| F | Traitement + archivage Drive | à venir |
| G | Historique + dashboard | à venir |

## Avertissement sécurité

La base PostgreSQL contient (table `app_setting`) les tokens OAuth Google en clair. **Ces credentials ne doivent pas être exportés ni partagés.** Le mot de passe PG lui-même est stocké en clair dans `C:\facto\config\application.properties` (généré par l'installeur, NTFS restreint à l'utilisateur). Une éventuelle encryption au repos est repoussée à une phase ultérieure.

## Spécification complète

Le prompt source définissant la vision, le modèle de données, les routes UI et la logique métier est conservé hors-repo. Les choix techniques de chaque étape sont documentés dans les commits.
