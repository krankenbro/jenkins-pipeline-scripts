package jobs.scripts;

class Packaging {

    private static String DefaultBranchOrCommitPR = '${sha1}'
    private static String DefaultBranchOrCommitPush = '*/master'
    private static String DefaultRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
    private static String DefaultMSBuild = 'MSBuild 15.0'
    private static String DefaultSharedLibName = 'virto-shared-library'

    /*
    private def Context;

    Packaging(script) {
        Context = script;
    }
    */

    /**
     * Creates new docker image
     *
     * @param context Execution Contex, typically "this"
     * @param dockerImageName name of the docker image to create, something like virtocommerce/storefront
     * @param dockerContextFolder folder that docker build will use for context, files from that folder can be added to the docker image
     * @param dockerSourcePath Source of the files to be included in the docker image, can be files within contextFolder or remote source referred by http
     * @param version current version of the build
     * @return reference to a docker image created
     */
    def static createDockerImage(context, String dockerImageName, String dockerContextFolder, String dockerSourcePath, String version) {
        def dockerFileFolder = dockerImageName.replaceAll("/", ".")
        context.echo "Building docker image \"${dockerImageName}\" using \"${dockerContextFolder}\" as context folder"
        context.bat "copy \"..\\workspace@libs\\virto-shared-library\\resources\\docker\\${dockerFileFolder}\\Dockerfile\" \"${dockerContextFolder}\" /Y"
        def dockerImage
        context.dir(dockerContextFolder)
        {
            dockerImage = context.docker.build("${dockerImageName}:${version}".toLowerCase(), "--build-arg SOURCE=\"${dockerSourcePath}\" .")
        }
        return dockerImage
    }

    def static startDockerTestEnvironment(context, String dockerTag)
    {
        def composeFolder = Utilities.getComposeFolder(context)
        context.dir(composeFolder)
        {
            def platformPort = Utilities.getPlatformPort(context)
            def storefrontPort = Utilities.getStorefrontPort(context)
            def sqlPort = Utilities.getSqlPort(context)

            context.echo "DOCKER_PLATFORM_PORT=${platformPort}"
            // 1. stop containers
            // 2. remove instances including database
            // 3. start up new containers
            context.withEnv(["DOCKER_TAG=${dockerTag}", "DOCKER_PLATFORM_PORT=${platformPort}", "DOCKER_STOREFRONT_PORT=${storefrontPort}", "DOCKER_SQL_PORT=${sqlPort}", "COMPOSE_PROJECT_NAME=${context.env.BUILD_TAG}" ]) {
                context.bat "docker-compose stop"
                context.bat "docker-compose rm -f"
                context.bat "docker-compose up -d"
            }
        }
    }

    def static stopDockerTestEnvironment(context, String dockerTag)
    {
        def composeFolder = Utilities.getComposeFolder(context)
        context.dir(composeFolder)
        {
            context.withEnv(["DOCKER_TAG=${dockerTag}", "COMPOSE_PROJECT_NAME=${context.env.BUILD_TAG}"]) {
                context.bat "docker-compose stop"
            }
        }
    }

    def static createSampleData(context)
    {
    	def wsFolder = context.pwd()
 	    context.bat "powershell.exe -File \"${wsFolder}\\..\\workspace@libs\\${DefaultSharedLibName}\\resources\\azure\\vc-setup-sampledata.ps1\" -apiurl \"${Utilities.getPlatformHost(context)}\" -ErrorAction Stop"
    }

    def static installModules(context)
    {
    	def wsFolder = context.pwd()
 	    context.bat "powershell.exe -File \"${wsFolder}\\..\\workspace@libs\\${DefaultSharedLibName}\\resources\\azure\\vc-setup-modules.ps1\" -apiurl \"${Utilities.getPlatformHost(context)}\" -ErrorAction Stop"
    }    

    def static pushDockerImage(context, dockerImage, String dockerTag)
    {
		context.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'docker-hub-credentials', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
			context.sh "docker login --password=${context.PASSWORD} --username=${context.USERNAME}"
		}
		dockerImage.push(dockerTag)
    }

    def static createReleaseArtifact(context, version, webProject, zipArtifact, websiteDir)
    {
        context.echo "Preparing release for ${version}"
        def tempFolder = Utilities.getTempFolder(context)
        def websitePath = Utilities.getWebPublishFolder(context, websiteDir)     
        def packagesDir = Utilities.getArtifactFolder(context)

        context.dir(packagesDir)
        {
            context.deleteDir()
        }

        // create artifacts
        context.bat "\"${context.tool DefaultMSBuild}\" \"${webProject}\" /nologo /verbosity:m /p:Configuration=Release /p:Platform=\"Any CPU\" /p:DebugType=none \"/p:OutputPath=$tempFolder\""

        (new AntBuilder()).zip(destfile: "${packagesDir}\\${zipArtifact}.${version}.zip", basedir: "${websitePath}")
    }

    def static buildSolutions(context)
    {
        def solutions = context.findFiles(glob: '*.sln')

        if (solutions.size() > 0) {
            for (int i = 0; i < solutions.size(); i++)
            {
                def solution = solutions[i]
                Packaging.runBuild(context, solution.name)
            }
        }
    }    

    def static runBuild(context, solution)
    {
		context.bat "Nuget restore ${solution}"

        def sqScannerMsBuildHome = context.tool 'Scanner for MSBuild'
        def fullJobName = context.env.JOB_NAME.replace("vc-2-org/", "").replace("/", "-")
        context.withSonarQubeEnv('VC Sonar Server') {
            // Due to SONARMSBRU-307 value of sonar.host.url and credentials should be passed on command line
            context.bat "\"${sqScannerMsBuildHome}\\SonarQube.Scanner.MSBuild.exe\" begin /k:\"${fullJobName}\" /d:\"sonar.organization=virtocommerce\" /d:sonar.host.url=%SONAR_HOST_URL% /d:sonar.login=%SONAR_AUTH_TOKEN%"
            context.bat "\"${context.tool DefaultMSBuild}\" \"${solution}\" /p:Configuration=Debug /p:Platform=\"Any CPU\" /property:RunCodeAnalysis=true"        
            context.bat "\"${sqScannerMsBuildHome}\\SonarQube.Scanner.MSBuild.exe\" end"
        }
    }

    def static runUnitTests(context, tests)
    {
        def xUnit = context.env.XUnit
        def xUnitExecutable = "${xUnit}\\xunit.console.exe"
        
        String paths = ""
        for(int i = 0; i < tests.size(); i++)
        {
            def test = tests[i]
            paths += "\"$test.path\" "
        }
                
        context.bat "${xUnitExecutable} ${paths} -xml xUnit.Test.xml -trait \"category=ci\" -parallel none"
        context.step([$class: 'XUnitPublisher', testTimeMargin: '3000', thresholdMode: 1, thresholds: [[$class: 'FailedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: ''], [$class: 'SkippedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: '']], tools: [[$class: 'XUnitDotNetTestType', deleteOutputFiles: true, failIfNotNew: false, pattern: '*.Test.xml', skipNoTestFiles: true, stopProcessingIfError: false]]])
    }

    def static publishRelease(context, version)
    {
        def tokens = "${context.env.JOB_NAME}".tokenize('/')
        def REPO_NAME = tokens[1]
        def REPO_ORG = "VirtoCommerce"

        def tempFolder = Utilities.getTempFolder(context)
        def packagesDir = Utilities.getArtifactFolder(context)

        context.dir(packagesDir)
        {
            def artifacts = context.findFiles(glob: '*.zip')
            if (artifacts.size() > 0) {
                for (int i = 0; i < artifacts.size(); i++)
                {
                    def artifact = artifacts[i]
                    context.bat "${context.env.Utils}\\github-release release --user $REPO_ORG --repo $REPO_NAME --tag v${version}"
                    context.bat "${context.env.Utils}\\github-release upload --user $REPO_ORG --repo $REPO_NAME --tag v${version} --name \"${artifact}\" --file \"${artifact}\""
                    context.echo "uploaded to https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
                    return "https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
                }
            }
        }
    }    

    def static getShouldPublish(context)
    {
		context.bat "\"${context.tool 'Git'}\" log -1 --pretty=%%B > LAST_COMMIT_MESSAGE"
		def git_last_commit = context.readFile('LAST_COMMIT_MESSAGE')			

		if (context.env.BRANCH_NAME == 'master' && git_last_commit.contains('[publish]')) {
			return true
		}

        return false
    }    

    def static updateModulesDefinitions(context, def directory, def module, def version)
    {
        context.dir(directory)
        {
            context.bat "\"${context.tool 'Git'}\" config user.email \"ci@virtocommerce.com\""
            context.bat "\"${context.tool 'Git'}\" config user.name \"Virto Jenkins\""
            /*
            if(!foundRecord)
                {
                    bat "\"${tool 'Git'}\" commit -am \"Updated module ${id}\""
                }
                else
                {
                    bat "\"${tool 'Git'}\" commit -am \"Added new module ${id}\""
                }
                */
            context.bat "\"${context.tool 'Git'}\" commit -am \"${module} ${version}\""
            context.bat "\"${context.tool 'Git'}\" push origin HEAD:master -f"
        }
    }    

    def static installModule(context, path)
    {
        def wsFolder = context.pwd()
 	    context.bat "powershell.exe -File \"${wsFolder}\\..\\workspace@libs\\${DefaultSharedLibName}\\resources\\azure\\vc-install-module.ps1\" -apiurl \"${Utilities.getPlatformHost(context)}\" -moduleZipArchievePath \"${path}\" -ErrorAction Stop"
    }    
}