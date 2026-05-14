# build-install-zip.ps1
# ---------------------------------------------------------------
# Construit le zip d'installation de facto pour expedition (cle USB).
#
# Sortie : C:\zzz\workspace\automation\facto-output\facto-install.zip
#
# Contenu du zip :
#   install-facto.ps1
#   facto-<version>.jar
#   jdk-21\           (depuis C:\zzz\my config\java\jdk-21)
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

$JdkSource  = 'C:\zzz\my config\java\jdk-21'
$OutputDir  = 'C:\zzz\workspace\automation\facto-output'
$ZipName    = 'facto-install.zip'
$ZipPath    = Join-Path $OutputDir $ZipName
$StagingDir = Join-Path $OutputDir 'facto-install'
$Installer  = Join-Path $RepoRoot 'install-facto.ps1'

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

# --- Build (sauf si -SkipBuild) ---
if ($SkipBuild) {
    Write-Host "[1/5] Build ignore (-SkipBuild)."
} else {
    Write-Host "[1/5] mvnw clean package..." -ForegroundColor Yellow
    Push-Location $RepoRoot
    try {
        & .\mvnw.cmd clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Echec du build Maven (exit $LASTEXITCODE)." }
    } finally {
        Pop-Location
    }
    Write-Host "      Build OK."
}

# --- Localiser le JAR produit ---
$jar = Get-ChildItem -Path (Join-Path $RepoRoot 'target') -Filter 'facto-*.jar' -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' -and $_.Name -notlike '*-original*' } |
       Sort-Object LastWriteTime -Descending |
       Select-Object -First 1
if (-not $jar) {
    throw "Aucun JAR 'facto-*.jar' trouve dans $(Join-Path $RepoRoot 'target')."
}
Write-Host "[2/5] JAR localise : $($jar.Name) ($([math]::Round($jar.Length / 1MB, 1)) Mo)"

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
Write-Host "[3/5] Staging pret : $StagingDir"

# --- Assembler le contenu ---
Copy-Item -Path $Installer -Destination (Join-Path $StagingDir 'install-facto.ps1') -Force
Copy-Item -Path $jar.FullName -Destination (Join-Path $StagingDir $jar.Name) -Force

Write-Host "[4/5] Copie du JDK 21 (~300 Mo, peut prendre 20-40s)..." -NoNewline
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
   - Detecte le service PostgreSQL et te demande le mot de passe de 'postgres'.
   - Verifie si Java 21+ est deja installe sur le systeme. Si oui, l'utilise.
     Sinon, copie le JDK 21 portable fourni dans le zip.
   - Cree le dossier C:\facto et y place : JDK (le cas echeant), facto.jar,
     launch-facto.ps1, config\application.properties (le password PG) et plus
     tard les logs (logs\).
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
Write-Host "[5/5] Compression vers $ZipName..." -NoNewline
Compress-Archive -Path (Join-Path $StagingDir '*') -DestinationPath $ZipPath -CompressionLevel Optimal
Write-Host " OK"

# --- Nettoyer le staging ---
Remove-Item -Path $StagingDir -Recurse -Force

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
Write-Host "    jdk-21\"
Write-Host "    README.txt"
Write-Host ""
