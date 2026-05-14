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
# Etape 1/8 : Detecter une version de Java >= 21 deja installee
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
    Write-Host "[1/8] Java $($systemJava.Major) detecte sur le systeme :"
    Write-Host "      $($systemJava.Javaw)"
    Write-Host "      Le JDK fourni dans le zip ne sera PAS copie."
    $useBundledJdk = $false
    $javawPath = $systemJava.Javaw
} else {
    if (-not (Test-Path $srcJdk -PathType Container)) {
        throw "Aucun Java 21+ detecte sur le systeme et le JDK 'jdk-21' est absent du zip ($srcDir)."
    }
    Write-Host "[1/8] Aucun Java 21+ detecte sur le systeme, on utilisera le JDK fourni."
}

# ---------------------------------------------------------------
# Etape 2/8 : Detecter le service PostgreSQL et collecter le mot de passe
# ---------------------------------------------------------------
$pgService = Get-Service -Name 'postgresql*' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $pgService) {
    Write-Host ""
    Write-Host "[2/8] Aucun service PostgreSQL detecte." -ForegroundColor Red
    Write-Host ""
    Write-Host "Facto utilise PostgreSQL comme base de donnees. Installer d'abord le service" -ForegroundColor Yellow
    Write-Host "depuis : https://www.postgresql.org/download/windows/" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Apres installation, creer la base 'facto' :"                                   -ForegroundColor Yellow
    Write-Host "    psql -U postgres -c `"CREATE DATABASE facto`""                              -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Appuie sur Entree pour fermer"
    exit 1
}

Write-Host "[2/8] Service PG detecte : $($pgService.Name) ($($pgService.Status))"
Write-Host ""
Write-Host "Saisis le mot de passe de l'utilisateur PostgreSQL 'postgres'."
Write-Host "(Laisse vide si pas de mot de passe configure ; saisie masquee.)"
$pgPwdSecure = Read-Host -Prompt "Mot de passe PG" -AsSecureString
$pgPwdPlain  = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($pgPwdSecure)
)
Write-Host ""

# ---------------------------------------------------------------
# Etape 3/8 : Creer C:\facto et accorder les droits a l'utilisateur
# ---------------------------------------------------------------
if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir | Out-Null
}

# Donner Modify (lecture/ecriture/suppression) heritable a l'utilisateur cible.
# /T applique sur l'arborescence existante, (OI)(CI) propage aux nouveaux enfants.
$icaclsArgs = @($InstallDir, '/grant', "${TargetUser}:(OI)(CI)M", '/T', '/C')
$null = & icacls.exe @icaclsArgs 2>&1
Write-Host "[3/8] Dossier $InstallDir pret, droits accordes a $TargetUser"

# ---------------------------------------------------------------
# Etape 4/8 : Ecrire la config externe (mot de passe PG)
# ---------------------------------------------------------------
$cfgDir  = Join-Path $InstallDir 'config'
$cfgFile = Join-Path $cfgDir 'application.properties'
if (-not (Test-Path $cfgDir)) {
    New-Item -ItemType Directory -Path $cfgDir | Out-Null
}
$cfgLines = @(
    '# Genere par install-facto.ps1 - NE PAS COMMITER',
    '# Surcharge appliquee via -Dspring.config.additional-location=file:C:/facto/config/',
    "spring.datasource.password=$pgPwdPlain"
)
Set-Content -Path $cfgFile -Value $cfgLines -Encoding UTF8
# Restreindre l'acces au seul utilisateur cible (le fichier contient un mot de passe en clair).
$null = & icacls.exe $cfgFile '/inheritance:r' '/grant' "${TargetUser}:(R,W)" '/grant' 'SYSTEM:(R,W)' '/grant' 'Administrators:(F)' 2>&1
Write-Host "[4/8] Config PG ecrite : $cfgFile (acces restreint a $TargetUser)"

# ---------------------------------------------------------------
# Etape 5/8 : Copier le JDK fourni si necessaire
# ---------------------------------------------------------------
if ($useBundledJdk) {
    $dstJdk = Join-Path $InstallDir 'jdk-21'
    if (Test-Path (Join-Path $dstJdk 'release')) {
        Write-Host "[5/8] JDK deja present dans $dstJdk, copie ignoree."
    } else {
        Write-Host "[5/8] Copie du JDK 21 (~300 Mo, peut prendre 30-60s)..." -NoNewline
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
    Write-Host "[5/8] JDK fourni ignore (Java systeme utilise)."
}

# ---------------------------------------------------------------
# Etape 6/8 : Copier le JAR (renomme en facto.jar pour stabilite)
# ---------------------------------------------------------------
$dstJar = Join-Path $InstallDir 'facto.jar'
Copy-Item -Path $srcJar.FullName -Destination $dstJar -Force
Write-Host "[6/8] JAR copie : $dstJar"

# ---------------------------------------------------------------
# Etape 7/8 : Generer le launcher PowerShell
# ---------------------------------------------------------------
$launcherPath = Join-Path $InstallDir 'launch-facto.ps1'

# Template du launcher : here-string SINGLE-quoted pour que rien ne soit
# interprete par PowerShell pendant la generation. On substitue ensuite
# les placeholders __INSTALL_DIR__ et __JAVA_PATH__ par les vraies valeurs.
$launcherTemplate = @'
# launch-facto.ps1 (genere par install-facto.ps1)
# Lance facto si elle ne tourne pas deja, puis ouvre le navigateur.
#
# Mode normal : fenetre console visible qui affiche les etapes de demarrage
#               (parse facto.log pour detecter les milestones Spring Boot).
# Mode -Silent (tache planifiee au logon) : start-and-go, aucune fenetre.

param([switch]$Silent)
$ErrorActionPreference = 'SilentlyContinue'

$installDir = '__INSTALL_DIR__'
$java       = '__JAVA_PATH__'
$jar        = Join-Path $installDir 'facto.jar'
$logFile    = Join-Path $installDir 'logs\facto.log'
$url        = 'http://localhost:8080'

function Test-AppRunning {
    try {
        $null = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 1 -ErrorAction Stop
        return $true
    } catch { return $false }
}

# Lit le log a partir d'un offset, en partage ReadWrite (le fichier est en cours
# d'ecriture par Java). Retourne '' si le fichier n'existe pas encore ou erreur.
function Read-LogTail {
    param([string]$Path, [int64]$FromOffset)
    if (-not (Test-Path $Path)) { return '' }
    try {
        $fs = [System.IO.File]::Open($Path, 'Open', 'Read', 'ReadWrite')
        try {
            if ($fs.Length -le $FromOffset) { return '' }
            $fs.Position = $FromOffset
            $reader = New-Object System.IO.StreamReader($fs)
            return $reader.ReadToEnd()
        } finally { $fs.Close() }
    } catch { return '' }
}

if (-not (Test-Path $java)) {
    if (-not $Silent) {
        Write-Host "Java introuvable : $java" -ForegroundColor Red
        Read-Host "Appuie sur Entree pour fermer"
    }
    exit 1
}

# Si Facto tourne deja, on ouvre simplement le navigateur (rien en silent).
if (Test-AppRunning) {
    if (-not $Silent) { Start-Process $url }
    exit 0
}

# Memorise la taille actuelle du log AVANT de lancer Java, pour ne detecter
# que les milestones du nouveau demarrage (le log est append-only).
$initialLogSize = 0
if (Test-Path $logFile) { $initialLogSize = (Get-Item $logFile).Length }

# Demarre Java en arriere-plan, sans console.
$cfgLoc  = ('file:' + ($installDir -replace '\\','/') + '/config/')
$jvmArgs = @("-Dfacto.home=$installDir", "-Dspring.config.additional-location=$cfgLoc", '-jar', $jar)
$javaProc = Start-Process -FilePath $java -ArgumentList $jvmArgs `
                          -WorkingDirectory $installDir -WindowStyle Hidden -PassThru

if ($Silent) { exit 0 }

# --------------- UI console : etapes de demarrage ---------------
$steps = @(
    @{ Pattern = 'Starting FactoApplication';      Label = 'Demarrage du moteur Java' }
    @{ Pattern = 'Bootstrapping Spring Data JPA';  Label = 'Initialisation de Spring Boot' }
    @{ Pattern = 'HikariPool-1 - Start completed'; Label = 'Connexion a la base de donnees' }
    @{ Pattern = 'Tomcat started on port';         Label = 'Serveur web operationnel' }
    @{ Pattern = 'Started FactoApplication';       Label = 'Facto prete' }
)
$stepDone = @{}
foreach ($s in $steps) { $stepDone[$s.Pattern] = $false }

$Host.UI.RawUI.WindowTitle = 'Facto - Demarrage'
try { [Console]::CursorVisible = $false } catch {}
Clear-Host
Write-Host ''
Write-Host '  ==================================================' -ForegroundColor Cyan
Write-Host '    Demarrage de Facto'                                -ForegroundColor Cyan
Write-Host '  ==================================================' -ForegroundColor Cyan
Write-Host ''
$stepLineStart = [Console]::CursorTop
foreach ($s in $steps) {
    Write-Host ('    [...]   ' + $s.Label) -ForegroundColor DarkGray
}
Write-Host ''
$statusLineY = [Console]::CursorTop
Write-Host ''  # reserve la ligne de statut
Write-Host ''
Write-Host '    Le navigateur s''ouvrira automatiquement des que Facto est prete.' -ForegroundColor DarkGray

function Write-Step {
    param([int]$Index, [string]$Marker, [string]$Label, [System.ConsoleColor]$Color)
    [Console]::SetCursorPosition(0, $script:stepLineStart + $Index)
    $line = '    [' + $Marker + ']   ' + $Label
    if ($line.Length -lt 70) { $line = $line + (' ' * (70 - $line.Length)) }
    Write-Host $line -ForegroundColor $Color
}

function Write-Status {
    param([string]$Text, [System.ConsoleColor]$Color = [System.ConsoleColor]::Yellow)
    [Console]::SetCursorPosition(0, $script:statusLineY)
    if ($Text.Length -lt 70) { $Text = $Text + (' ' * (70 - $Text.Length)) }
    Write-Host $Text -ForegroundColor $Color
}

$startTime = Get-Date
$maxWaitSeconds = 120

while ($true) {
    $elapsed = [int]((Get-Date) - $startTime).TotalSeconds

    # Detection des milestones via lecture incrementale du log
    $logTail = Read-LogTail -Path $logFile -FromOffset $initialLogSize
    if ($logTail) {
        for ($i = 0; $i -lt $steps.Length; $i++) {
            $s = $steps[$i]
            if (-not $stepDone[$s.Pattern] -and $logTail.Contains($s.Pattern)) {
                $stepDone[$s.Pattern] = $true
                Write-Step -Index $i -Marker 'OK' -Label $s.Label -Color Green
            }
        }
    }

    Write-Status "    Demarrage en cours depuis $elapsed s..."

    # App prete ?
    if (Test-AppRunning) {
        for ($i = 0; $i -lt $steps.Length; $i++) {
            $s = $steps[$i]
            if (-not $stepDone[$s.Pattern]) {
                $stepDone[$s.Pattern] = $true
                Write-Step -Index $i -Marker 'OK' -Label $s.Label -Color Green
            }
        }
        Write-Status "    Facto prete en $elapsed s. Ouverture du navigateur..." -Color Green
        Start-Sleep -Milliseconds 800
        Start-Process $url
        Start-Sleep -Seconds 2
        try { [Console]::CursorVisible = $true } catch {}
        exit 0
    }

    # Java s'est arrete avant d'etre pret ?
    if ($javaProc -and $javaProc.HasExited) {
        Write-Status "    Java s'est arrete (code $($javaProc.ExitCode)). Verifie le log." -Color Red
        Write-Host ''
        Write-Host "    Log : $logFile" -ForegroundColor Yellow
        Write-Host ''
        try { [Console]::CursorVisible = $true } catch {}
        Read-Host '    Appuie sur Entree pour fermer'
        exit 1
    }

    if ($elapsed -gt $maxWaitSeconds) {
        Write-Status "    Demarrage > $maxWaitSeconds s, il y a peut-etre un probleme." -Color Red
        Write-Host ''
        Write-Host "    Log : $logFile" -ForegroundColor Yellow
        Write-Host ''
        try { [Console]::CursorVisible = $true } catch {}
        Read-Host '    Appuie sur Entree pour fermer'
        exit 1
    }

    Start-Sleep -Milliseconds 500
}
'@

$launcherContent = $launcherTemplate.Replace('__INSTALL_DIR__', $InstallDir).Replace('__JAVA_PATH__', $javawPath)
Set-Content -Path $launcherPath -Value $launcherContent -Encoding UTF8
Write-Host "[7/8] Launcher genere : $launcherPath"

# ---------------------------------------------------------------
# Etape 8/8 : Raccourci bureau + tache planifiee
# ---------------------------------------------------------------
$wshell  = New-Object -ComObject WScript.Shell
$shortcut = $wshell.CreateShortcut($DesktopShortcut)
$shortcut.TargetPath       = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
$shortcut.Arguments        = "-NoProfile -ExecutionPolicy Bypass -File `"$launcherPath`""
$shortcut.WorkingDirectory = $InstallDir
$shortcut.IconLocation     = "$javawPath,0"
$shortcut.Description      = "Lancer $AppName et ouvrir le navigateur"
$shortcut.WindowStyle      = 1   # Normale (fenetre visible avec progression)
$shortcut.Save()
Write-Host "      Raccourci bureau cree : $DesktopShortcut"

# --- Tache planifiee au logon ---
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

Write-Host "      Tache planifiee enregistree : $TaskName (au logon)"

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
Write-Host "  Logs                   : $InstallDir\logs\"
Write-Host "  Config PG (creds)      : $cfgFile"
Write-Host "  Base de donnees        : PostgreSQL (service local, base 'facto')"
Write-Host ""

if ($NoStart) {
    Write-Host "Pour lancer maintenant : double-clique sur l'icone $AppName du bureau."
} else {
    Write-Host "Demarrage de l'application..." -ForegroundColor Cyan
    Start-Process -FilePath (Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe') `
                  -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$launcherPath`"")
}

Write-Host ""
Read-Host "Appuie sur Entree pour fermer cette fenetre"
