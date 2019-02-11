#!groovy
import jobs.scripts.*

// module script
def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
    
	node {
	    def storeName = config.sampleStore
		projectType = config.projectType
		if(projectType==null){
			projectType = 'Theme'
		}
		try {

			stage('Checkout') {
				timestamps { 
					deleteDir()
					checkout scm
				}
			}

			stage('Build') {
				timestamps { 
                    //Packaging.startSonarJS(this)
					//Packaging.runGulpBuild(this)
					def sqScannerMsBuildHome = tool 'Scanner for MSBuild'
					def fullJobName = Utilities.getRepoName(this)

					withSonarQubeEnv('VC Sonar Server') {
						bat "\"${sqScannerMsBuildHome}\\sonar-scanner-3.0.3.778\\bin\\sonar-scanner.bat\" scan -Dsonar.projectKey=${fullJobName}_${env.BRANCH_NAME} -Dsonar.sources=./src -Dsonar.branch=${env.BRANCH_NAME} -Dsonar.projectName=\"${fullJobName}\" -Dsonar.host.url=%SONAR_HOST_URL% -Dsonar.login=%SONAR_AUTH_TOKEN%"
        			}

					bat "npm install"
					bat "npm run build"
				}
			}

			// stage('Unit Tests'){
			// 	timestamps{
			// 		bat "npm run test"
			// 	}
			// }

			// stage('E2E'){
			// 	timestamps{
			// 		bat "npm run e2e"
			// 	}
			// }

			// stage('Code Analysis'){
            //     timestamps{
            //         Packaging.checkAnalyzerGate(this)
            //     }
            // }

			stage('test deploy'){
				timestamps{
					zip zipFile: "theme.zip" dir: "dist\\*"
					Packaging.themeDeploy(this)
				}
			}

			if(params.themeResultZip != null){
                def artifacts = findFiles(glob: 'artifacts/*.zip')
                for(artifact in artifacts){
                    bat "copy /Y \"${artifact.path}\" \"${params.themeResultZip}\""
                }
            }
			
			def version = Utilities.getPackageVersion(this)

			// if (Packaging.getShouldStage(this)) {
			// 	stage('Stage') {
			// 		timestamps {
			// 		    def stagingName = Utilities.getStagingNameFromBranchName(this)
			// 			Utilities.runSharedPS(this, "resources\\azure\\VC-Theme2Azure.ps1", /-StagingName "${stagingName}" -StoreName "${storeName}"/)
			// 		}
			// 	}			
			// }

			if (env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'stage') {
				stage('Publish') {
					timestamps { 
						if(params.themeResultZip == null){
							// Packaging.publishRelease(this, version, "")
						}
					}
				}
			}
		}
		catch (any) {
			currentBuild.result = 'FAILURE'
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally {
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
		}
	}
}
