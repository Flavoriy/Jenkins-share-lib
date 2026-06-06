def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Build Docker Image',
        imageRepository: '',
        imageTag: '',
        dockerfile: 'Dockerfile',
        context: '.',
        buildArgs: []
    ] + config

    ConfigValidator.requireUnix(this, 'dockerBuild')
    ConfigValidator.requireValue(this, cfg.imageRepository, 'imageRepository')

    String imageTag = cfg.imageTag?.toString()?.trim() ?: env.IMAGE_TAG ?: ConfigValidator.resolveImageTag(this, cfg)
    String imageRef = "${cfg.imageRepository}:${imageTag}"

    stage(cfg.stageName as String) {
        String dockerfileArg = cfg.dockerfile?.toString()?.trim()
            ? "-f ${ConfigValidator.shellQuote(cfg.dockerfile as String)}"
            : ''
        String buildArgs = formatBuildArgs(cfg.buildArgs)
        String context = cfg.context?.toString()?.trim() ?: '.'

        sh """
            set -e
            docker build ${dockerfileArg} ${buildArgs} -t '${imageRef}' '${context}'
        """
    }

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
