[CmdletBinding()]
param(
    [string]$ConfigPath = 'C:\Program Files\MongoDB\Server\9.0\bin\mongod.cfg'
)

$ErrorActionPreference = 'Stop'
$identidad = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identidad)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Abre PowerShell como administrador y vuelve a ejecutar este script.'
}

$configReal = (Resolve-Path -LiteralPath $ConfigPath).Path
$servicio = Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Services\MongoDB'
if ($servicio.ImagePath -notmatch [regex]::Escape($configReal)) {
    throw 'La ruta no corresponde a la configuracion del servicio MongoDB. No se modifico nada.'
}
$java = (Get-Command java -ErrorAction Stop).Source
$mavenRepo = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2\repository\org\mongodb'
$jars = @(
    (Join-Path $mavenRepo 'mongodb-driver-sync\5.8.1\mongodb-driver-sync-5.8.1.jar'),
    (Join-Path $mavenRepo 'mongodb-driver-core\5.8.1\mongodb-driver-core-5.8.1.jar'),
    (Join-Path $mavenRepo 'bson\5.8.1\bson-5.8.1.jar')
)
foreach ($jar in $jars) {
    if (-not (Test-Path -LiteralPath $jar)) { throw "Dependencia ausente: $jar. Compila el proyecto primero." }
}

$original = [IO.File]::ReadAllText($configReal)
if ($original -notmatch '(?m)^\s*bindIp:\s*127\.0\.0\.1\s*$') {
    throw 'Este asistente solo admite MongoDB limitado a 127.0.0.1. No se modifico nada.'
}
if ($original -match '(?m)^replication\s*:') {
    if ($original -notmatch '(?m)^\s+replSetName:\s*rs0\s*$') {
        throw 'Ya existe otra configuracion de replicacion. No se modifico nada.'
    }
    Start-Service -Name MongoDB
} else {
    $respaldo = "$configReal.staycontrol.$([DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')).bak"
    Copy-Item -LiteralPath $configReal -Destination $respaldo
    Write-Host "Copia de seguridad: $respaldo"
    Stop-Service -Name MongoDB
    try {
        # Mantiene las rutas, opciones y datos originales; solo agrega la replicacion.
        [IO.File]::WriteAllText($configReal, $original.TrimEnd() + "`r`n`r`nreplication:`r`n  replSetName: rs0`r`n", [Text.UTF8Encoding]::new($false))
        Start-Service -Name MongoDB
    } catch {
        Stop-Service -Name MongoDB -ErrorAction SilentlyContinue
        Copy-Item -LiteralPath $respaldo -Destination $configReal -Force
        Start-Service -Name MongoDB
        throw
    }
}

# El helper verifica el estado y nunca reconfigura un replica set existente.
& $java -cp ($jars -join ';') (Join-Path $PSScriptRoot 'MongoLocal.java') --iniciar-rs0
if ($LASTEXITCODE -ne 0) { throw 'No se confirmo rs0 PRIMARY. Revisa el mensaje anterior; no se borraron datos.' }
Write-Host 'Listo. Reinicia StayControl. Compass: mongodb://localhost:27017/?replicaSet=rs0'
