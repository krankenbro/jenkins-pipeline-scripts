param(
    [parameter(Mandatory = $true)]
    $RepoOrg,
    [parameter(Mandatory = $true)]
    $RepoName
)
$api = "https://api.github.com/repos/${RepoOrg}/${RepoName}/releases/latest"

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$headerAuth = "Bearer ${GITHUB_API_TOKEN}"
$header = @{}
$header.Add("Authorization", $headerAuth)
$result = Invoke-RestMethod -Method Get -Headers $header -Uri $api
try {
    Write-Output $result.assets[0].url
}
catch{
    Write-Output $result
    exit 1
}