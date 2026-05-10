# install-facto.ps1
# ---------------------------------------------------------------
# Installeur autonome de l'application "facto" pour Windows.
#
# Ce script :
#   1. Demande l'elevation administrateur (UAC)
#   2. Cree C:\facto et y donne les droits a l'utilisateur courant
#   3. Detecte une version de Java >= 21 deja installee. Si presente,
#      utilise celle-la. Sinon copie le JDK 21 portable du zip.
#   4. Copie facto.jar dans C:\facto
#   5. Genere C:\facto\launch-facto.ps1
#   6. Cree un raccourci "Facto" sur le bureau
#   7. Cree une tache planifiee "Facto Auto-Start" qui demarre l'app
#      en arriere-plan a chaque ouverture de session Windows
#   8. Lance l'application et ouvre le navigateur
#
# Pre-requis : ce script est livre avec, dans le meme dossier :
#   - jdk-21\           (JDK Temurin 21 portable, utilise si aucun
#                        Java 21+ n'est detecte sur le systeme)
#   - facto-*.jar       (JAR Spring Boot)
# ---------------------------------------------------------------

#Requires -Version 5.1

[CmdletBinding()]
param(
    [switch]$NoStart,
    [switch]$Elevated  # interne : marqueur signalant qu'on tourne deja en mode eleve
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# --- Constantes ---
$AppName         = 'Facto'
$TaskName        = 'Facto Auto-Start'
$InstallDir      = 'C:\facto'
$DesktopShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) "$AppName.lnk"
$AppUrl          = 'http://localhost:8080'

# ---------------------------------------------------------------
# Auto-elevation : si pas admin, on relance en mode eleve via UAC
# ---------------------------------------------------------------
$identity  = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
$isAdmin   = $principal.IsInRole([Security.Principal.WindowsBuiltinRole]::Administrator)

if (-not $isAdmin) {
    Write-Host ""
    Write-Host "Cette installation cree le dossier $InstallDir et necessite donc"
    Write-Host "des droits administrateur. Une fenetre UAC va apparaitre..."
    Write-Host ""

    $argList = @('-NoProfile', '-NoExit', '-ExecutionPolicy', 'Bypass',
                 '-File', "`"$PSCommandPath`"",
                 '-Elevated')
    if ($NoStart) { $argList += '-NoStart' }

    try {
        Start-Process -FilePath 'powershell.exe' -ArgumentList $argList -Verb RunAs
    } catch {
        Write-Host "Elevation refusee ou annulee. Installation interrompue." -ForegroundColor Red
        Read-Host "Appuie sur Entree pour fermer"
        exit 1
    }
    exit 0
}

# ---------------------------------------------------------------
# A partir d'ici, on est admin.
# ---------------------------------------------------------------

# L'utilisateur cible (qui possede C:\facto en lecture/ecriture) est
# celui qui a lance le script, pas forcement l'admin.
# Quand on s'auto-eleve via Verb RunAs, $env:USERNAME reste celui de
# l'utilisateur courant (UAC ne change pas d'utilisateur).
$TargetUser = "$env:USERDOMAIN\$env:USERNAME"

Write-Host ""
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "  Installation de $AppName"                     -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host ""

# --- Localiser les sources (a cote du script) ---
$srcDir = $PSScriptRoot
if ([string]::IsNullOrEmpty($srcDir)) { $srcDir = (Get-Location).Path }

$srcJdk = Join-Path $srcDir 'jdk-21'
$srcJar = Get-ChildItem -Path $srcDir -Filter 'facto-*.jar' -ErrorAction SilentlyContinue |
          Select-Object -First 1
if (-not $srcJar) {
    throw "Aucun JAR 'facto-*.jar' trouve dans : $srcDir"
}

Write-Host "Source       : $srcDir"
Write-Host "Cible        : $InstallDir"
Write-Host "Utilisateur  : $TargetUser"
Write-Host ""

# ---------------------------------------------------------------
# Helper : recuperer la sortie d'un .exe sans declencher de
# NativeCommandError terminal sous $ErrorActionPreference = 'Stop'.
# (Windows PowerShell 5.1 wrappe chaque ligne stderr en ErrorRecord
# quand on fait `& exe 2>&1` et coupe avec EAP=Stop. On bascule
# temporairement EAP en Continue autour de l'invocation.)
# ---------------------------------------------------------------
function Invoke-NativeForOutput {
    param(
        [Parameter(Mandatory)] [string]   $Exe,
        [Parameter()]          [string[]] $Arguments = @()
    )
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = & $Exe @Arguments 2>&1 | ForEach-Object { "$_" }
        return ($lines -join "`n")
    } finally {
        $ErrorActionPreference = $prev
    }
}

# ---------------------------------------------------------------
# Etape 1/7 : Detecter une version de Java >= 21 deja installee
# ---------------------------------------------------------------
function Find-SystemJava {
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $cmd) { return $null }

    $verOutput = Invoke-NativeForOutput -Exe $cmd.Source -Arguments @('-version')

    if ($verOutput -notmatch 'version\s+"(\d+)') {
        return $null
    }
    $major = [int]$Matches[1]
    if ($major -lt 21) { return $null }

    $javawPath = Join-Path (Split-Path $cmd.Source -Parent) 'javaw.exe'
    if (-not (Test-Path $javawPath)) { return $null }

    return [PSCustomObject]@{
        Major = $major
        Java  = $cmd.Source
        Javaw = $javawPath
    }
}

$systemJava = Find-SystemJava
$useBundledJdk = $true
$javawPath = $null

if ($systemJava) {
    Write-Host "[1/7] Java $($systemJava.Major) detecte sur le systeme :"
    Write-Host "      $($systemJava.Javaw)"
    Write-Host "      Le JDK fourni dans le zip ne sera PAS copie."
    $useBundledJdk = $false
    $javawPath = $systemJava.Javaw
} else {
    if (-not (Test-Path $srcJdk -PathType Container)) {
        throw "Aucun Java 21+ detecte sur le systeme et le JDK 'jdk-21' est absent du zip ($srcDir)."
    }
    Write-Host "[1/7] Aucun Java 21+ detecte sur le systeme, on utilisera le JDK fourni."
}

# ---------------------------------------------------------------
# Etape 2/7 : Creer C:\facto et accorder les droits a l'utilisateur
# ---------------------------------------------------------------
if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir | Out-Null
}

# Donner Modify (lecture/ecriture/suppression) heritable a l'utilisateur cible.
# /T applique sur l'arborescence existante, (OI)(CI) propage aux nouveaux enfants.
$icaclsArgs = @($InstallDir, '/grant', "${TargetUser}:(OI)(CI)M", '/T', '/C')
$null = & icacls.exe @icaclsArgs 2>&1
Write-Host "[2/7] Dossier $InstallDir pret, droits accordes a $TargetUser"

# ---------------------------------------------------------------
# Etape 3/7 : Copier le JDK fourni si necessaire
# ---------------------------------------------------------------
if ($useBundledJdk) {
    $dstJdk = Join-Path $InstallDir 'jdk-21'
    if (Test-Path (Join-Path $dstJdk 'release')) {
        Write-Host "[3/7] JDK deja present dans $dstJdk, copie ignoree."
    } else {
        Write-Host "[3/7] Copie du JDK 21 (~300 Mo, peut prendre 30-60s)..." -NoNewline
        Copy-Item -Path $srcJdk -Destination $InstallDir -Recurse -Force
        Write-Host " OK"
    }
    $javawPath = Join-Path $dstJdk 'bin\javaw.exe'
    $javaExe   = Join-Path $dstJdk 'bin\java.exe'
    if (-not (Test-Path $javaExe))   { throw "java.exe introuvable apres copie : $javaExe" }
    if (-not (Test-Path $javawPath)) { throw "javaw.exe introuvable apres copie : $javawPath" }
    $verOutput = Invoke-NativeForOutput -Exe $javaExe -Arguments @('-version')
    if ($verOutput -notmatch 'version\s+"21\.') {
        Write-Warning "Version Java inattendue (attendu : 21) :`n$verOutput"
    }
} else {
    Write-Host "[3/7] JDK fourni ignore (Java systeme utilise)."
}

# ---------------------------------------------------------------
# Etape 4/7 : Copier le JAR (renomme en facto.jar pour stabilite)
# ---------------------------------------------------------------
$dstJar = Join-Path $InstallDir 'facto.jar'
Copy-Item -Path $srcJar.FullName -Destination $dstJar -Force
Write-Host "[4/7] JAR copie : $dstJar"

# ---------------------------------------------------------------
# Etape 5/7 : Generer le launcher PowerShell
# ---------------------------------------------------------------
$launcherPath = Join-Path $InstallDir 'launch-facto.ps1'

# Template du launcher : here-string SINGLE-quoted pour que rien ne soit
# interprete par PowerShell pendant la generation. On substitue ensuite
# les placeholders __INSTALL_DIR__ et __JAVA_PATH__ par les vraies valeurs.
$launcherTemplate = @'
# launch-facto.ps1 (genere par install-facto.ps1)
# Lance facto si elle ne tourne pas deja, puis ouvre le navigateur.
# Avec -Silent : demarre seulement, n'ouvre pas le navigateur.

param([switch]$Silent)
$ErrorActionPreference = 'SilentlyContinue'

$installDir = '__INSTALL_DIR__'
$java       = '__JAVA_PATH__'
$jar        = Join-Path $installDir 'facto.jar'
$url        = 'http://localhost:8080'

function Test-AppRunning {
    try {
        $null = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 1 -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
}

if (-not (Test-Path $java)) {
    if (-not $Silent) {
        Write-Host "Java introuvable : $java" -ForegroundColor Red
        Read-Host "Appuie sur Entree pour fermer"
    }
    exit 1
}

if (-not (Test-AppRunning)) {
    $jvmArgs = @('-Dfacto.home=__INSTALL_DIR__', '-jar', $jar)
    Start-Process -FilePath $java -ArgumentList $jvmArgs -WorkingDirectory $installDir -WindowStyle Hidden | Out-Null

    if (-not $Silent) {
        for ($i = 0; $i -lt 60; $i++) {
            Start-Sleep -Milliseconds 500
            if (Test-AppRunning) { break }
        }
    }
}

if (-not $Silent) {
    Start-Process $url
}
'@

$launcherContent = $launcherTemplate.Replace('__INSTALL_DIR__', $InstallDir).Replace('__JAVA_PATH__', $javawPath)
Set-Content -Path $launcherPath -Value $launcherContent -Encoding UTF8
Write-Host "[5/7] Launcher genere : $launcherPath"

# ---------------------------------------------------------------
# Etape 6/7 : Raccourci bureau
# ---------------------------------------------------------------
$wshell  = New-Object -ComObject WScript.Shell
$shortcut = $wshell.CreateShortcut($DesktopShortcut)
$shortcut.TargetPath       = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
$shortcut.Arguments        = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$launcherPath`""
$shortcut.WorkingDirectory = $InstallDir
$shortcut.IconLocation     = "$javawPath,0"
$shortcut.Description      = "Lancer $AppName et ouvrir le navigateur"
$shortcut.WindowStyle      = 7   # Minimise sans focus
$shortcut.Save()
Write-Host "[6/7] Raccourci bureau cree : $DesktopShortcut"

# ---------------------------------------------------------------
# Etape 7/7 : Tache planifiee au logon
# ---------------------------------------------------------------
$existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existing) {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
}

$action = New-ScheduledTaskAction `
    -Execute (Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe') `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$launcherPath`" -Silent" `
    -WorkingDirectory $InstallDir

$trigger = New-ScheduledTaskTrigger -AtLogOn -User $TargetUser

$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Hours 0)

$taskPrincipal = New-ScheduledTaskPrincipal `
    -UserId $TargetUser `
    -LogonType Interactive `
    -RunLevel Limited

Register-ScheduledTask `
    -TaskName    $TaskName `
    -Action      $action `
    -Trigger     $trigger `
    -Settings    $settings `
    -Principal   $taskPrincipal `
    -Description "Lance $AppName en arriere-plan a l'ouverture de session." | Out-Null

Write-Host "[7/7] Tache planifiee enregistree : $TaskName (au logon)"

# ---------------------------------------------------------------
# Recap
# ---------------------------------------------------------------
Write-Host ""
Write-Host "==============================================" -ForegroundColor Green
Write-Host "  Installation reussie."                        -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Dossier d'installation : $InstallDir"
Write-Host "  Java utilise           : $javawPath"
if (-not $useBundledJdk) {
    Write-Host "                           (Java systeme detecte, JDK fourni non copie)"
}
Write-Host "  Raccourci bureau       : $DesktopShortcut"
Write-Host "  Demarrage auto         : tache '$TaskName' (au logon)"
Write-Host "  URL de l'application   : $AppUrl"
Write-Host "  Logs et base H2        : $InstallDir\logs\, $InstallDir\db\"
Write-Host ""

if ($NoStart) {
    Write-Host "Pour lancer maintenant : double-clique sur l'icone $AppName du bureau."
} else {
    Write-Host "Demarrage de l'application..." -ForegroundColor Cyan
    Start-Process -FilePath (Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe') `
                  -ArgumentList @('-NoProfile','-WindowStyle','Hidden','-ExecutionPolicy','Bypass','-File',"`"$launcherPath`"") `
                  -WindowStyle Hidden
}

Write-Host ""
Read-Host "Appuie sur Entree pour fermer cette fenetre"
