def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Push Docker Image',
        imageRef: '',
        imageRepository: '',
        imageTag: '',
        registry: 'ghcr.io',
        credentialsId: ''
    ] + config

    ConfigValidator.requireUnix(this, 'dockerPush')
    ConfigValidator.requireValue(this, cfg.credentialsId, 'credentialsId')

    String imageRef = resolveImageRef(cfg)
    ConfigValidator.requireValue(this, imageRef, 'imageRef or imageRepository')

    stage(cfg.stageName as String) {
        withCredentials([usernamePassword(credentialsId: cfg.credentialsId as String, usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_TOKEN')]) {
            sh """
                set -e
                set +x
                echo "\$REGISTRY_TOKEN" | docker login '${cfg.registry}' -u "\$REGISTRY_USERNAME" --password-stdin
                set -x
                docker push '${imageRef}'
                docker logout '${cfg.registry}'
            """
        }
    }

    env.IMAGE_REF = imageRef
    return imageRef
}

private String resolveImageRef(Map cfg) {
    if (cfg.imageRef?.toString()?.trim()) {
        return cfg.imageRef.toString().trim()
    }

    if (env.IMAGE_REF?.trim()) {
        return env.IMAGE_REF
    }

    if (cfg.imageRepository?.toString()?.trim()) {
        String imageTag = cfg.imageTag?.toString()?.trim() ?: env.IMAGE_TAG ?: ConfigValidator.resolveImageTag(this, cfg)
        env.IMAGE_REPOSITORY = cfg.imageRepository as String
        env.IMAGE_TAG = imageTag
        return "${cfg.imageRepository}:${imageTag}"
    }

    return ''
}
