# PLATFORM_SAMPLE_URL - url where to get modules info
# PLATFORM_SAMPLE_APPID - app id
# PLATFORM_SAMPLE_SECRET - Secret Key

Param(
    [parameter(Mandatory = $true)]
    $outFile )

function Add-ModuleBundle
{
    param($module)
    if($module.PSObject.Properties.Name -contains "groups")
    {
        $module.groups += "hap"
    }
    else
    {
        $hapGroup = ("hap")
        $module | Add-Member -Name "groups" -Value $hapGroup -MemberType NoteProperty
    }
}
function Fix-Version
{
    param($module, $version)
    $module.version = $version
}
function Update-Dependencies
{
    param($virtoModule, $moduleInfo)
    $virtoModule.dependencies.Clear()
    $virtoModule.dependencies = $moduleInfo.dependencies
}

. $PSScriptRoot\utilities.ps1

if ([string]::IsNullOrWhiteSpace($hmacAppId))
{
    $hmacAppId = "${env:PLATFORM_SAMPLE_APPID}"
}

if ([string]::IsNullOrWhiteSpace($hmacSecret))
{
    $hmacSecret = "${env:PLATFORM_SAMPLE_SECRET}"
}

$checkModulesUrl = "${env:PLATFORM_SAMPLE_URL}/api/platform/modules"
$virtoModulesUrl = "https://raw.githubusercontent.com/VirtoCommerce/vc-modules/master/modules.json"

# Initiate sample data installation
$headerValue = Create-Authorization $hmacAppId $hmacSecret
$headers = @{}
$headers.Add("Authorization", $headerValue)

$virtoModules = Invoke-RestMethod $virtoModulesUrl -Method Get -ErrorAction Stop


$modules = Invoke-RestMethod $checkModulesUrl -Method Get -Headers $headers -ErrorAction Stop
$installedModules = 0
if($modules.Length -le 0)
{
    Write-Output "No module's info returned"
    exit 1
}
Foreach($module in $modules)
{
    if($module.isInstalled){
        $installedModules++
    }

    if($module.isInstalled -and $module.id.Contains("VirtoCommerce"))
    {
        foreach($vModule in $virtoModules)
        {
            if($vModule.id -eq $module.id)
            {
                Add-ModuleBundle $vModule
                Fix-Version $vModule $module.version
                Update-Dependencies $vModule $module
                Write-Output $vModule
            }
        }
    }

    if($module.validationErrors.Length -gt 0){
        Write-Output $module.id
        Write-Output $module.validationErrors
        exit 1
    }
}
Write-Output "Modules installed: $installedModules"
$virtoModules | ConvertTo-Json -Depth 3 | Out-File $outFile