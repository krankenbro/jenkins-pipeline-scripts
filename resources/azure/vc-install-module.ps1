Param(  
    [parameter(Mandatory = $true)]
    $apiurl,
    [parameter(Mandatory = $true)]
    $moduleZipArchievePath,
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


$moduleUploadUrl = "$apiurl/api/platform/modules/localstorage"
$moduleInstallUrl = "$apiurl/api/platform/modules/install"
$restartUrl = "$apiurl/api/platform/modules/restart"
$pushUrl = "$apiurl/api/platform/pushnotifications"

$headerValue = Create-Authorization $hmacAppId $hmacSecret
$headers = @{}
$headers.Add("Authorization", $headerValue)

$moduleUploadResult = Invoke-MultipartFormDataUpload -InFile $moduleZipArchievePath -Uri $moduleUploadUrl -Authorization $headerValue
Write-Output $moduleUploadResult
$moduleInstallResult = Invoke-RestMethod -Uri $moduleInstallUrl -Method Post -Headers $headers -Body $moduleUploadResult
$notificationId = $moduleInstallResult.id
$NotificationStateJson = @"
     {"Ids":["$notificationId"],"start":0, "count": 1}
"@

$notify = @{}
do {
    Start-Sleep -s 3
    $state = Invoke-RestMethod "$pushUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers
    Write-Output $state
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