def call(Map config = [:], Closure body) {
    List<String> values = []
    values.addAll(tiktoCommonCiEnv(config))
    values.addAll(ConfigValidator.normalizeList(config.sharedSecretEnv))
    values.addAll(ConfigValidator.normalizeList(config.appSecretEnv))

    withEnv(values) {
        body.call()
    }
}
