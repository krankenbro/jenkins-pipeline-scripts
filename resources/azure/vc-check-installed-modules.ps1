Param(
    [parameter(Mandatory = $true)]
    $apiurl,
    $hmacAppId,
    $hmacSecret
)

. $PSScriptRoot\utilities.ps1

if ([string]::IsNullOrWhiteSpace($hmacAppId)) {
    $hmacAppId = "${env:HMAC_APP_ID}"
}

if ([string]::IsNullOrWhiteSpace($hmacSecret)) {
    $hmacSecret = "${env:HMAC_SECRET}"
}

$checkModulesUrl = "$apiurl/api/platform/modules"
$restartUrl = "$apiurl/api/platform/modules/restart"

$headerValue = Create-Authorization $hmacAppId $hmacSecret
$headers = @{}
$headers.Add("Authorization", $headerValue)

Write-Output "restart again"
$moduleState = Invoke-RestMethod "$restartUrl" -Method Post -ContentType "application/json" -Headers $headers
Start-Sleep -s 5

$modules = Invoke-RestMethod $checkModulesUrl -Method Get -Headers $headers -ErrorAction Stop
Write-Output $modules
$installedModules = 0
if ($modules.Length -le 0) {
    Write-Output "No module's info returned"
    exit 1
}
Foreach ($module in $modules) {
    if ($module.isInstalled) {
        $installedModules++
    }
    if ($module.validationErrors.Length -gt 0) {
        Write-Output $module.id
        Write-Output $module.validationErrors
        exit 1
    }
}
Write-Output "Modules installed: $installedModules"
if($false -and $installedModules -lt 20){
    exit 1
}