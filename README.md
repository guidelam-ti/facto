# facto

Application web **locale** (lancée via `java -jar facto.jar`, accessible sur `http://localhost:8080`) qui automatise la récupération, l'organisation et l'archivage de factures reçues par courriel.

- **Source** : Gmail (compte personnel)
- **Destination** : Google Drive (compte personnel)

## Stack

- Java 21 (LTS)
- Spring Boot 4.0.x — Spring Web, Thymeleaf, Spring Data JPA, validation
- Thymeleaf 3 + Layout Dialect
- Bootstrap 5.3 + Bootstrap Icons + police Inter (servis localement, pas de CDN)
- H2 en mode fichier (`~/.facto/db/`)
- Pas de Spring Security (app loopback uniquement)

## Démarrage rapide

Prérequis : **Java 21** (JDK 17 ne fonctionne pas avec Spring Boot 4).

```sh
# Build
./mvnw clean package

# Run
java -jar target/facto-0.1.0-SNAPSHOT.jar

# Open
http://localhost:8080
```

La console H2 (utile en dev) est exposée sur `http://localhost:8080/h2-console` — JDBC URL : `jdbc:h2:file:~/.facto/db/facto;AUTO_SERVER=TRUE`, user `sa`, mot de passe vide.

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

À partir de l'étape C, la base H2 (`~/.facto/db/facto.mv.db`) contiendra les tokens OAuth Google en clair. **Ce fichier ne doit pas être partagé ni commité.** Une éventuelle encryption au repos est repoussée à une phase ultérieure.

## Spécification complète

Le prompt source définissant la vision, le modèle de données, les routes UI et la logique métier est conservé hors-repo. Les choix techniques de chaque étape sont documentés dans les commits.
