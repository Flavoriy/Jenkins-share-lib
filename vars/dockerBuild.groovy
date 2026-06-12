def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Build Docker Image',
        wrapStage: true,
        imageRepository: '',
        imageTag: '',
        additionalImageRefs: [],
        dockerfile: 'Dockerfile',
        context: '.',
        buildArgs: [],
        secretEnvFileCredentialsId: ''
    ] + config

    ConfigValidator.requireUnix(this, 'dockerBuild')
    ConfigValidator.requireValue(this, cfg.imageRepository, 'imageRepository')

    String imageTag = cfg.imageTag?.toString()?.trim() ?: env.IMAGE_TAG ?: ConfigValidator.resolveImageTag(this, cfg)
    String imageRef = "${cfg.imageRepository}:${imageTag}"
    List imageRefs = ([imageRef] + ConfigValidator.normalizeList(cfg.additionalImageRefs)).unique()

    Closure body = {
        String dockerfileArg = cfg.dockerfile?.toString()?.trim()
            ? "-f ${ConfigValidator.shellQuote(cfg.dockerfile as String)}"
            : ''
        String buildArgs = formatBuildArgs(cfg.buildArgs)
        String tagArgs = imageRefs
            .collect { ref -> "-t ${ConfigValidator.shellQuote(ref as String)}" }
            .join(' ')
        String context = cfg.context?.toString()?.trim() ?: '.'

        Closure build = {
            String secretBlock = cfg.secretEnvFileCredentialsId?.toString()?.trim()
                ? '''
                    set -a
                    . "$SECRET_ENV"
                    set +a
                '''
                : ''

            sh """
                set -e
                set +x
                ${secretBlock}
                docker build ${dockerfileArg} ${buildArgs} ${tagArgs} ${ConfigValidator.shellQuote(context)}
            """
        }

        if (cfg.secretEnvFileCredentialsId?.toString()?.trim()) {
            withCredentials([file(credentialsId: cfg.secretEnvFileCredentialsId as String, variable: 'SECRET_ENV')]) {
                build.call()
            }
        } else {
            build.call()
        }
    }

    runWithOptionalStage(cfg, body)

    env.IMAGE_REPOSITORY = cfg.imageRepository as String
    env.IMAGE_TAG = imageTag
    env.IMAGE_REF = imageRef

    return imageRef
}

private String formatBuildArgs(Object buildArgs) {
    if (buildArgs instanceof Map) {
        return buildArgs.collect { key, value ->
            "--build-arg ${ConfigValidator.shellQuote("${key}=${value}")}"
        }.join(' ')
    }

    return ConfigValidator.normalizeList(buildArgs).join(' ')
}

private void runWithOptionalStage(Map cfg, Closure body) {
    if (cfg.wrapStage == null || cfg.wrapStage.toString().toBoolean()) {
        stage(cfg.stageName as String) {
            body.call()
        }
        return
    }

    body.call()
}
