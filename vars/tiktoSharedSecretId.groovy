def call(Map config = [:]) {
    return ConfigValidator.firstNonBlank(env.AWS_SHARED_SECRET_ID, config.sharedSecretId) ?: 'jenkins/tikto/shared'
}
