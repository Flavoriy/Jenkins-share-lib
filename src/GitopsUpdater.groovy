class GitopsUpdater implements Serializable {
    private final Object script

    GitopsUpdater(Object script) {
        this.script = script
    }

    boolean update(Map config = [:]) {
        String repoUrl = ConfigValidator.firstNonBlank(config.repoUrl, config.manifestRepoUrl)
        String branch = ConfigValidator.firstNonBlank(config.branch, config.manifestBranch) ?: 'main'
        String credentialsId = ConfigValidator.firstNonBlank(config.credentialsId, config.gitCredentialsId, config.manifestGitCredentialsId)
        String checkoutDir = ConfigValidator.firstNonBlank(config.checkoutDir, config.manifestCheckoutDir) ?: '.gitops-manifest'
        String manifestFile = ConfigValidator.firstNonBlank(config.file, config.manifestFile)
        String imageRef = ConfigValidator.firstNonBlank(config.imageRef, script.env.IMAGE_REF)
        String imageRepository = ConfigValidator.firstNonBlank(config.imageRepository, inferImageRepository(imageRef))

        ConfigValidator.requireHttpsGitUrl(script, repoUrl, 'manifestRepoUrl')
        ConfigValidator.requireValue(script, credentialsId, 'manifestGitCredentialsId')
        ConfigValidator.requireValue(script, manifestFile, 'manifestFile')
        ConfigValidator.requireValue(script, imageRef, 'imageRef')

        boolean changed = false

        script.dir(checkoutDir) {
            script.git branch: branch, credentialsId: credentialsId, url: repoUrl

            script.withEnv([
                "IMAGE_REF=${imageRef}",
                "IMAGE_REPOSITORY=${imageRepository}",
                "MANIFEST_FILE=${manifestFile}"
            ]) {
                runManifestUpdate(config)
            }

            script.sh """
                set -e
                git config user.name ${ConfigValidator.shellQuote(ConfigValidator.firstNonBlank(config.gitUserName) ?: 'jenkins')}
                git config user.email ${ConfigValidator.shellQuote(ConfigValidator.firstNonBlank(config.gitUserEmail) ?: 'jenkins@local')}
                git add ${ConfigValidator.shellQuote(manifestFile)}
            """

            int diffStatus = script.sh(script: 'git diff --cached --quiet', returnStatus: true)
            if (diffStatus == 0) {
                script.echo 'GitOps manifest unchanged.'
                script.env.MANIFEST_CHANGED = 'false'
            } else {
                String commitMessage = ConfigValidator.firstNonBlank(config.commitMessage) ?: "chore: update image to ${imageRef}"
                script.sh "git commit -m ${ConfigValidator.shellQuote(commitMessage)}"
                script.env.MANIFEST_CHANGED = 'true'
                changed = true
            }

            boolean shouldPush = config.push == null ? true : (config.push as boolean)
            if (shouldPush && changed) {
                pushChanges(repoUrl, branch, credentialsId)
            }
        }

        return changed
    }

    private void runManifestUpdate(Map config) {
        List commands = ConfigValidator.normalizeList(config.updateCommands ?: config.manifestUpdateCommands)

        if (commands) {
            commands.each { command ->
                script.sh command
            }
            return
        }

        script.sh '''
            set -e
            test -f "$MANIFEST_FILE"
            sed -i "s#image: ${IMAGE_REPOSITORY}.*#image: ${IMAGE_REF}#g" "$MANIFEST_FILE"
            grep -n "$IMAGE_REF" "$MANIFEST_FILE"
        '''
    }

    private void pushChanges(String repoUrl, String branch, String credentialsId) {
        String remotePath = repoUrl.replaceFirst(/^https:\/\//, '')

        script.withCredentials([script.usernamePassword(credentialsId: credentialsId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_TOKEN')]) {
            script.sh """
                set -e
                set +x
                git remote set-url origin "https://\$GIT_USERNAME:\$GIT_TOKEN@${remotePath}"
                set -x
                git push origin HEAD:${branch}
            """
        }
    }

    private static String inferImageRepository(String imageRef) {
        int separator = imageRef.lastIndexOf(':')
        if (separator <= 0) {
            return imageRef
        }

        return imageRef.substring(0, separator)
    }
}
