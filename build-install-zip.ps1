# build-install-zip.ps1
# ---------------------------------------------------------------
# Construit le zip d'installation de facto pour expedition (cle USB).
#
# Sortie : C:\zzz\workspace\automation\facto-output\facto-install.zip
#
# Contenu du zip :
#   install-facto.ps1
#   facto-<version>.jar       (jar courant, PostgreSQL only)
#   facto-migration.jar       (jar legacy depuis commit 026e6c3 contenant
#                              le migrateur H2 -> PG, declenche par
#                              l'installeur si une base H2 est trouvee
#                              dans C:\facto\db\)
#   jdk-21\                   (depuis C:\zzz\my config\java\jdk-21)
#
# Par defaut, le script lance "mvnw clean package" pour rebuilder
# le JAR avant de zipper. Utilise -SkipBuild si tu as deja un JAR
# a jour dans target\.
# ---------------------------------------------------------------

#Requires -Version 5.1

[CmdletBinding()]
param(
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# --- Constantes ---
$RepoRoot   = $PSScriptRoot
if ([string]::IsNullOrEmpty($RepoRoot)) { $RepoRoot = (Get-Location).Path }

$JdkSource     = 'C:\zzz\my config\java\jdk-21'
$OutputDir     = 'C:\zzz\workspace\automation\facto-output'
$ZipName       = 'facto-install.zip'
$ZipPath       = Join-Path $OutputDir $ZipName
$StagingDir    = Join-Path $OutputDir 'facto-install'
$WorktreeDir   = Join-Path $OutputDir 'migration-worktree'
$Installer     = Join-Path $RepoRoot 'install-facto.ps1'
# Commit qui contient encore le driver H2 + H2ToPostgresMigrator (etape 2 du plan PG).
# Le jar bati a partir de ce commit sert UNIQUEMENT a migrer les donnees H2 d'un ancien
# install de facto vers PostgreSQL au moment de l'upgrade.
$MigrationCommit = '026e6c3'

# --- Banniere ---
Write-Host ""
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "  Construction du zip d'installation facto"     -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Repo       : $RepoRoot"
Write-Host "  JDK source : $JdkSource"
Write-Host "  Sortie     : $ZipPath"
Write-Host ""

# --- Verifications ---
if (-not (Test-Path $JdkSource -PathType Container)) {
    throw "JDK source introuvable : $JdkSource"
}
if (-not (Test-Path $Installer -PathType Leaf)) {
    throw "install-facto.ps1 introuvable : $Installer"
}

# --- Build du jar courant (sauf si -SkipBuild) ---
if ($SkipBuild) {
    Write-Host "[1/6] Build ignore (-SkipBuild)."
} else {
    Write-Host "[1/6] mvnw clean package..." -ForegroundColor Yellow
    Push-Location $RepoRoot
    try {
        & .\mvnw.cmd clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Echec du build Maven (exit $LASTEXITCODE)." }
    } finally {
        Pop-Location
    }
    Write-Host "      Build OK."
}

# --- Localiser le JAR courant ---
$jar = Get-ChildItem -Path (Join-Path $RepoRoot 'target') -Filter 'facto-*.jar' -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' -and $_.Name -notlike '*-original*' } |
       Sort-Object LastWriteTime -Descending |
       Select-Object -First 1
if (-not $jar) {
    throw "Aucun JAR 'facto-*.jar' trouve dans $(Join-Path $RepoRoot 'target')."
}
Write-Host "[2/6] JAR localise : $($jar.Name) ($([math]::Round($jar.Length / 1MB, 1)) Mo)"

# --- Build du jar de migration depuis le commit etape-2 (via git worktree) ---
$migrationJarOut = Join-Path $OutputDir 'facto-migration.jar'
Write-Host "[3/6] Build du jar de migration H2 -> PG depuis commit $MigrationCommit..." -ForegroundColor Yellow
if (Test-Path $WorktreeDir) {
    & git worktree remove --force $WorktreeDir 2>$null | Out-Null
    if (Test-Path $WorktreeDir) { Remove-Item -Path $WorktreeDir -Recurse -Force }
}
Push-Location $RepoRoot
try {
    & git worktree add --detach $WorktreeDir $MigrationCommit
    if ($LASTEXITCODE -ne 0) { throw "git worktree add a echoue (exit $LASTEXITCODE)." }
} finally {
    Pop-Location
}
Push-Location $WorktreeDir
try {
    & .\mvnw.cmd clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Build du jar de migration a echoue (exit $LASTEXITCODE)." }
    $migJarBuilt = Get-ChildItem -Path (Join-Path $WorktreeDir 'target') -Filter 'facto-*.jar' |
                   Where-Object { $_.Name -notlike '*-original*' } |
                   Select-Object -First 1
    if (-not $migJarBuilt) { throw "Jar de migration introuvable apres build." }
    Copy-Item -Path $migJarBuilt.FullName -Destination $migrationJarOut -Force
} finally {
    Pop-Location
}
& git worktree remove --force $WorktreeDir 2>$null | Out-Null
$migJarSize = [math]::Round((Get-Item $migrationJarOut).Length / 1MB, 1)
Write-Host "      Jar de migration : facto-migration.jar ($migJarSize Mo)"

# --- Preparer le dossier d'output et de staging ---
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}
if (Test-Path $StagingDir) {
    Remove-Item -Path $StagingDir -Recurse -Force
}
if (Test-Path $ZipPath) {
    Remove-Item -Path $ZipPath -Force
}
New-Item -ItemType Directory -Path $StagingDir | Out-Null
Write-Host "[4/6] Staging pret : $StagingDir"

# --- Assembler le contenu ---
Copy-Item -Path $Installer -Destination (Join-Path $StagingDir 'install-facto.ps1') -Force
Copy-Item -Path $jar.FullName -Destination (Join-Path $StagingDir $jar.Name) -Force
Copy-Item -Path $migrationJarOut -Destination (Join-Path $StagingDir 'facto-migration.jar') -Force

Write-Host "[5/6] Copie du JDK 21 (~300 Mo, peut prendre 20-40s)..." -NoNewline
Copy-Item -Path $JdkSource -Destination (Join-Path $StagingDir 'jdk-21') -Recurse -Force
Write-Host " OK"

# --- README minimal ---
$readme = @"
facto - installation
====================

Prerequis : PostgreSQL doit deja etre installe en service sur Windows
            (https://www.postgresql.org/download/windows/) et une base nommee
            'facto' doit exister. Si ce n'est pas le cas, ouvrir une console
            admin et lancer :
                psql -U postgres -c "CREATE DATABASE facto"
            L'installeur facto demandera ensuite le mot de passe de l'utilisateur
            'postgres' pour pouvoir s'y connecter.

1. Copier ce dossier complet sur ton disque (Bureau, Documents, ...).
2. Clic droit sur "install-facto.ps1" -> "Executer avec PowerShell".
3. Si Windows bloque (SmartScreen) : "Plus d'infos" -> "Executer quand meme".
4. Si l'execution est refusee, ouvrir PowerShell et lancer :
       powershell.exe -ExecutionPolicy Bypass -File .\install-facto.ps1
5. Une fenetre UAC (controle administrateur) va apparaitre. Accepte :
   l'installation cree le dossier C:\facto, ce qui necessite des droits admin
   au moment de la creation. L'application elle-meme tournera ensuite sans admin.

Ce que fait l'installeur :
   - Detecte le service PostgreSQL, demande le mot de passe de 'postgres' et
     cree la base 'facto' si elle n'existe pas encore.
   - Verifie si Java 21+ est deja installe sur le systeme. Si oui, l'utilise.
     Sinon, copie le JDK 21 portable fourni dans le zip.
   - Cree le dossier C:\facto et y place : JDK (le cas echeant), facto.jar,
     launch-facto.ps1, config\application.properties (le password PG) et plus
     tard les logs (logs\).
   - Si une ancienne base H2 (C:\facto\db\facto.mv.db) est detectee, MIGRE
     automatiquement les donnees (fournisseurs, mappings, factures, tokens
     OAuth Google) vers PostgreSQL. Le fichier H2 est renomme en .migrated
     apres succes.
   - Cree un raccourci "Facto" sur le bureau.
   - Cree une tache planifiee qui demarre l'app a chaque ouverture de session.
   - Lance l'app immediatement et ouvre le navigateur sur http://localhost:8080.

Tout le runtime est dans C:\facto ; les donnees sont dans la base PG locale.

Logs                  : C:\facto\logs\facto.log
Config PG (creds)     : C:\facto\config\application.properties
Configuration runtime : C:\facto\launch-facto.ps1
Donnees               : base PostgreSQL 'facto' (service local)

Desinstaller :
   1. Get-ScheduledTask 'Facto Auto-Start' | Unregister-ScheduledTask -Confirm:`$false
   2. Tuer le processus javaw.exe (Gestionnaire des taches, ou: Stop-Process -Name javaw)
   3. Supprimer C:\facto (necessite admin pour supprimer le dossier racine)
   4. Supprimer le raccourci "Facto" du bureau
   5. (Optionnel) Supprimer la base PG : psql -U postgres -c "DROP DATABASE facto"
"@
Set-Content -Path (Join-Path $StagingDir 'README.txt') -Value $readme -Encoding UTF8

# --- Zipper ---
Write-Host "[6/6] Compression vers $ZipName..." -NoNewline
Compress-Archive -Path (Join-Path $StagingDir '*') -DestinationPath $ZipPath -CompressionLevel Optimal
Write-Host " OK"

# --- Nettoyer le staging ---
Remove-Item -Path $StagingDir -Recurse -Force
Remove-Item -Path $migrationJarOut -Force -ErrorAction SilentlyContinue

# --- Recap ---
$zipSize = [math]::Round((Get-Item $ZipPath).Length / 1MB, 1)
Write-Host ""
Write-Host "==============================================" -ForegroundColor Green
Write-Host "  Zip pret pour expedition."                    -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Fichier : $ZipPath"
Write-Host "  Taille  : $zipSize Mo"
Write-Host ""
Write-Host "  Contenu :"
Write-Host "    install-facto.ps1"
Write-Host "    $($jar.Name)"
Write-Host "    facto-migration.jar (one-shot, declenche si H2 detectee)"
Write-Host "    jdk-21\"
Write-Host "    README.txt"
Write-Host ""
