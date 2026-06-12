def call(String deployEnv = '', Map config = [:]) {
    String explicit = ConfigValidator.firstNonBlank(env.AWS_APP_SECRET_ID, config.appSecretId)
    if (explicit) {
        return explicit
    }

    String prefix = ConfigValidator.firstNonBlank(env.AWS_APP_SECRET_PREFIX, config.appSecretPrefix) ?: 'tikto'
    return deployEnv?.trim() ? "${prefix}/${deployEnv.trim()}/app" : ''
}
