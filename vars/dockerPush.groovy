def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Push Docker Image',
        wrapStage: true,
        imageRef: '',
        additionalImageRefs: [],
        imageRepository: '',
        imageTag: '',
        registry: 'ghcr.io',
        credentialsId: '',
        credentialType: 'usernamePassword',
        username: ''
    ] + config

    ConfigValidator.requireUnix(this, 'dockerPush')

    String imageRef = resolveImageRef(cfg)
    ConfigValidator.requireValue(this, imageRef, 'imageRef or imageRepository')
    List imageRefs = ([imageRef] + ConfigValidator.normalizeList(cfg.additionalImageRefs)).unique()

    Closure body = {
        String credentialType = cfg.credentialType?.toString()?.trim() ?: 'usernamePassword'

        if (credentialType in ['string', 'secretText']) {
            String username = cfg.username?.toString()?.trim() ?: env.REGISTRY_USERNAME
            ConfigValidator.requireValue(this, username, 'username')

            if (env.REGISTRY_TOKEN?.trim() || env.GHCR_TOKEN?.trim()) {
                pushImages(cfg.registry as String, username, imageRefs)
            } else {
                ConfigValidator.requireValue(this, cfg.credentialsId, 'credentialsId')
                withCredentials([string(credentialsId: cfg.credentialsId as String, variable: 'REGISTRY_TOKEN')]) {
                    pushImages(cfg.registry as String, username, imageRefs)
                }
            }
        } else if (env.REGISTRY_TOKEN?.trim() || env.GHCR_TOKEN?.trim()) {
            String username = cfg.username?.toString()?.trim() ?: env.REGISTRY_USERNAME ?: env.GHCR_USERNAME
            ConfigValidator.requireValue(this, username, 'username')
            pushImages(cfg.registry as String, username, imageRefs)
        } else {
            ConfigValidator.requireValue(this, cfg.credentialsId, 'credentialsId')
            withCredentials([usernamePassword(credentialsId: cfg.credentialsId as String, usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_TOKEN')]) {
                pushImages(cfg.registry as String, '$REGISTRY_USERNAME', imageRefs)
            }
        }
    }

    runWithOptionalStage(cfg, body)

    env.IMAGE_REF = imageRef
    return imageRef
}

private void pushImages(String registry, String username, List imageRefs) {
    String pushCommands = imageRefs
        .collect { imageRef -> "docker push ${ConfigValidator.shellQuote(imageRef as String)}" }
        .join('\n')

    sh """
        set -e
        set +x
        REGISTRY_TOKEN="\${REGISTRY_TOKEN:-\${GHCR_TOKEN:-}}"
        echo "\$REGISTRY_TOKEN" | docker login ${ConfigValidator.shellQuote(registry)} -u "${username}" --password-stdin
        ${pushCommands}
        docker logout ${ConfigValidator.shellQuote(registry)}
    """
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

private void runWithOptionalStage(Map cfg, Closure body) {
    if (cfg.wrapStage == null || cfg.wrapStage.toString().toBoolean()) {
        stage(cfg.stageName as String) {
            body.call()
        }
        return
    }

    body.call()
}
