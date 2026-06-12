def call(Map config = [:]) {
    String imageRepository = ConfigValidator.firstNonBlank(env.IMAGE_REPOSITORY, config.imageRepository)
    ConfigValidator.requireValue(this, imageRepository, 'imageRepository')

    String imageTag = ConfigValidator.firstNonBlank(env.IMAGE_TAG, config.imageTag)
    if (!imageTag) {
        String versionPrefix = ConfigValidator.firstNonBlank(env.IMAGE_VERSION_PREFIX, config.imageVersionPrefix)
        imageTag = versionPrefix ? "${versionPrefix}.${env.BUILD_NUMBER ?: '0'}" : ConfigValidator.resolveImageTag(this, config)
    }

    String imageRef = "${imageRepository}:${imageTag}"
    List additionalImageRefs = resolveAdditionalImageRefs(config, imageRepository)

    env.IMAGE_REPOSITORY = imageRepository
    env.IMAGE_TAG = imageTag
    env.IMAGE_REF = imageRef
    env.IMAGE_EXTRA_REF = additionalImageRefs ? additionalImageRefs.first().toString() : ''

    echo "Docker image target ref: ${imageRef}"
    additionalImageRefs.each { imageExtraRef ->
        echo "Docker image extra ref: ${imageExtraRef}"
    }

    return [
        imageRef: imageRef,
        additionalImageRefs: additionalImageRefs
    ]
}

private List resolveAdditionalImageRefs(Map config, String imageRepository) {
    List additionalImageRefs = ConfigValidator.normalizeList(config.additionalImageRefs)
    List extraTags = env.IMAGE_EXTRA_TAG?.trim()
        ? [env.IMAGE_EXTRA_TAG.trim()]
        : ConfigValidator.normalizeList(config.extraTags)

    additionalImageRefs.addAll(extraTags.collect { tag -> "${imageRepository}:${tag}" })
    return additionalImageRefs.unique()
}
