Param(  
    [parameter(Mandatory = $true)]
    $path
)

& "${env:NUGET}\nuget.exe" push "${path}" -Source https://hot-nuget.azurewebsites.net/nuget -ApiKey ${env:NUGET_KEY}