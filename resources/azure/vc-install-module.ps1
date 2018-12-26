Param(  
    [parameter(Mandatory = $true)]
    $apiurl,
    [parameter(Mandatory = $true)]
    $moduleZipArchievePath,
    $hmacAppId,
    $hmacSecret,
    $platformContainer
)

. $PSScriptRoot\utilities.ps1

if ([string]::IsNullOrWhiteSpace($hmacAppId)) {
    $hmacAppId = "${env:HMAC_APP_ID}"
}

if ([string]::IsNullOrWhiteSpace($hmacSecret)) {
    $hmacSecret = "${env:HMAC_SECRET}"
}


$moduleUploadUrl = "$apiurl/api/platform/modules/localstorage"
$moduleInstallUrl = "$apiurl/api/platform/modules/install"
$restartUrl = "$apiurl/api/platform/modules/restart"
$pushUrl = "$apiurl/api/platform/pushnotifications"

$headerValue = Create-Authorization $hmacAppId $hmacSecret
$headers = @{}
$headers.Add("Authorization", $headerValue)

Write-Output "Replace web.config"
docker cp C:\CICD\web.config ${platformContainer}:/vc-platform/
docker cp C:\CICD\modules.json ${platformContainer}:/vc-platform/
Write-Output "Restarting website"
Invoke-RestMethod "$restartUrl" -Method Post -ContentType "application/json" -Headers $headers
Start-Sleep -s 5

$moduleUploadResult = Invoke-MultipartFormDataUpload -InFile $moduleZipArchievePath -Uri $moduleUploadUrl -Authorization $headerValue
Write-Output $moduleUploadResult
$moduleInstallResult = Invoke-RestMethod -Uri $moduleInstallUrl -Method Post -Headers $headers -Body $moduleUploadResult
$notificationId = $moduleInstallResult.id
$NotificationStateJson = @"
     {"Ids":["$notificationId"],"start":0, "count": 1}
"@

$notify = @{}
do {
    $state = Invoke-RestMethod "$pushUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers
    Write-Output $state.notifyEvents
    if ($state.notifyEvents -ne $null ) {
        $notify = $state.notifyEvents
        if ($notify.errorCount -gt 0) {
            Write-Output $notify
            exit 1
        }
    }
}
while (([string]::IsNullOrEmpty($notify.finished)) -and $cycleCount -lt 180)
Start-Sleep -s 3
Write-Output "Restarting website"
$moduleState = Invoke-RestMethod "$restartUrl" -Method Post -ContentType "application/json" -Headers $headers