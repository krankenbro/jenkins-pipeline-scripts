Param(  
    [parameter(Mandatory = $true)]
    $path
)

& "${env:NUGET}\nuget.exe" push "${path}" -Source https://hot-nuget.azurewebsites.net -ApiKey ${env:NUGET_KEY}