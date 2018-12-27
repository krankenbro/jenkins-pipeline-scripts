Param(  
    [parameter(Mandatory = $true)]
    $apiurl,
    [parameter(Mandatory = $true)]
    $platformContainer,
    $hmacAppId,
    $hmacSecret,
    $needRestart
)

. $PSScriptRoot\utilities.ps1   

if ([string]::IsNullOrWhiteSpace($hmacAppId)) {
    $hmacAppId = "${env:HMAC_APP_ID}"
}

if ([string]::IsNullOrWhiteSpace($hmacSecret)) {
    $hmacSecret = "${env:HMAC_SECRET}"
}     

# Initialize paths used by the script
$modulesStateUrl = "$apiurl/api/platform/pushnotifications"
$modulesInstallUrl = "$apiurl/api/platform/modules/autoinstall"
$restartUrl = "$apiurl/api/platform/modules/restart"

# Call homepage, to make sure site is compiled
$initResult = Invoke-WebRequest $apiurl -UseBasicParsing
if ($initResult.StatusCode -ne 200) {
    # throw exception when site can't be opened
    throw "Can't open admin site homepage"
}

# Initiate modules installation
#$headerValue = Create-Authorization $hmacAppId $hmacSecret
#$headers = @{}
#$headers.Add("Authorization", $headerValue)

Write-Output "Replace web.config"
docker cp C:\CICD\web.config ${platformContainer}:/vc-platform/
docker cp C:\CICD\modules.json ${platformContainer}:/vc-platform/
docker cp C:\CICD\modules.zip ${platformContainer}:/vc-platform/
docker exec $platformContainer powershell -Command "Expand-Archive -Path C:\vc-platform\modules.zip -DestinationPath C:\vc-platform\Modules"
docker exec $platformContainer powershell -Command "Remove-Item C:\vc-platform\modules.zip -Force"
#Write-Output "Restarting website"
#$moduleState = Invoke-RestMethod "$restartUrl" -Method Post -ContentType "application/json" -Headers $headers
#Start-Sleep -s 3