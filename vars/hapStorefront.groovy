#!groovy
import jobs.scripts.*

// module script
def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
    
	node
	{
		// configuration parameters
		def hmacAppId = env.HMAC_APP_ID
		def hmacSecret = env.HMAC_SECRET
		def solution = config.solution
		projectType = config.projectType
		
		def webProject = 'VirtoCommerce.Platform.Web\\VirtoCommerce.Platform.Web.csproj'
		def zipArtifact = 'VirtoCommerce.Platform'
		def websiteDir = 'VirtoCommerce.Platform.Web'
		def deployScript = 'VC-Platform2AzureDev.ps1'
		def dockerTag = "${env.BRANCH_NAME}-branch"
		def buildOrder = Utilities.getNextBuildOrder(this)
		if (env.BRANCH_NAME == 'master') {
			deployScript = 'VC-Platform2AzureQA.ps1'
			dockerTag = "latest"
		}
		
		solution = "VirtoCommerce.Storefront.sln"
		projectType = "NETCORE2"

		
		websiteDir = 'VirtoCommerce.Storefront'
		webProject = 'VirtoCommerce.Storefront\\VirtoCommerce.Storefront.csproj'
		zipArtifact = 'VirtoCommerce.StoreFront'
		deployScript = 'VC-Storefront2AzureDev.ps1'
		if (env.BRANCH_NAME == 'master') {
			deployScript = 'VC-Storefront2AzureQA.ps1'
		}
		
		try {
			echo "Building branch ${env.BRANCH_NAME}"

			stage('Checkout') {
				timestamps { 
					if(env.BRANCH_NAME == 'master'){
						deleteDir()
					}
					checkout scm
				}				
			}

			stage('Build + Analyze') {		
				timestamps {		
					
					Packaging.startAnalyzer(this)
					Packaging.runBuild(this, solution)
				}
			}
		
			def version = Utilities.getAssemblyVersion(this, webProject)
			def dockerImage

			stage('Package') {
				timestamps { 
					Packaging.createReleaseArtifact(this, version, webProject, zipArtifact, websiteDir)
					if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
						def websitePath = Utilities.getWebPublishFolder(this, websiteDir)
						dockerImage = Packaging.createDockerImage(this, zipArtifact.replaceAll('\\.','/'), websitePath, ".", dockerTag)			
					}
				}
			}

			def tests = Utilities.getTestDlls(this)
			if(tests.size() > 0)
			{
				stage('Tests') {
					timestamps { 
						Packaging.runUnitTests(this, tests)
					}
				}
			}		

			stage('Submit Analysis') {
				timestamps { 
					Packaging.endAnalyzer(this)
				}
			}			

			// No need to occupy a node
			// stage("Quality Gate"){
			// 	Packaging.checkAnalyzerGate(this)
			// }

			if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'stage') {
				stage('Docker Test Environment') {
					timestamps { 
						// Start docker environment				
						Packaging.startDockerTestEnvironment(this, dockerTag)
						
						// install modules
						Packaging.installModules(this, 1)	

						// check installed modules
						Packaging.checkInstalledModules(this)

						// now create sample data
						Packaging.createSampleData(this)					
					}
				}

				stage('Theme build and deploy'){
					def themePath = "${env.WORKSPACE}@tmp\\theme.zip"
					build(job: "../hap-theme/${env.BRANCH_NAME}", parameters: [string(name: 'themeResultZip', value: themePath)])
					Packaging.installTheme(this, themePath)
				}

				stage('E2E')
				{
					timestamps 
					{
						Utilities.runE2E(this)
					}
				}
			}		

			// if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
			// 	stage('Publish'){
			// 		timestamps { 
			// 			if(solution == 'VirtoCommerce.Platform.sln' || projectType == 'NETCORE2')
			// 			{
			// 				Packaging.pushDockerImage(this, dockerImage, dockerTag)
			// 			}
			// 			if (Packaging.getShouldPublish(this)) {
			// 				def notes = Utilities.getReleaseNotes(this, webProject)
			// 				Packaging.publishRelease(this, version, notes)
			// 			}

			// 			if(solution == 'VirtoCommerce.Platform.sln' || projectType == 'NETCORE2')
			// 			{
			// 				Utilities.runSharedPS(this, "resources\\azure\\${deployScript}")
			// 			}

			// 			if(env.BRANCH_NAME == 'master')
			// 				Packaging.createNugetPackages(this)
			// 		}
			// 	}
			// }



			stage('Cleanup') {
				timestamps { 
					//Packaging.cleanBuild(this, solution)
					bat "dotnet build-server shutdown"
					bat "docker image prune --force"
				}
			}	
		
		}
		catch (any) {
			currentBuild.result = 'FAILURE'
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally {
			Packaging.stopDockerTestEnvironment(this, dockerTag)
			if(currentBuild.result != 'FAILURE') {
				step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])])
			}
			else {
				def log = currentBuild.rawBuild.getLog(300)
				def failedStageLog = Utilities.getFailedStageStr(log)
				def failedStageName = Utilities.getFailedStageName(failedStageLog)
				def mailBody = Utilities.getMailBody(this, failedStageName, failedStageLog)
				emailext body:mailBody, subject: "${env.JOB_NAME}:${env.BUILD_NUMBER} - ${currentBuild.currentResult}", recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]
			}
	    	//step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'dev@virtoway.com', sendToIndividuals: true])
		}
	
	}
}