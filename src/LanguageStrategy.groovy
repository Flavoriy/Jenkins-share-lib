class LanguageStrategy implements Serializable {
    static String detect(Object script, Map config = [:]) {
        String language = config.language?.toString()?.trim()?.toLowerCase()
        if (language && language != 'auto') {
            return language
        }

        if (script.fileExists('pom.xml')) {
            return 'maven'
        }

        if (script.fileExists('build.gradle') || script.fileExists('build.gradle.kts')) {
            return 'gradle'
        }

        if (script.fileExists('package.json')) {
            return 'node'
        }

        return 'shell'
    }

    static List buildCommands(Object script, Map config = [:]) {
        String language = detect(script, config)

        switch (language) {
            case 'maven':
                String skipTests = (config.skipTests as boolean) ? ' -DskipTests' : ''
                return ["${mavenCommand(script, config)} -B clean install${skipTests}"]
            case 'gradle':
                return ["${gradleCommand(script, config)} clean build"]
            case 'node':
                return ['npm ci', 'npm run build']
            case 'shell':
                return ConfigValidator.normalizeList(config.commands ?: config.buildCommands)
            default:
                script.error "Unsupported language for buildApp: ${language}"
        }
    }

    static List testCommands(Object script, Map config = [:]) {
        String language = detect(script, config)

        switch (language) {
            case 'maven':
                return ["${mavenCommand(script, config)} -B test"]
            case 'gradle':
                return ["${gradleCommand(script, config)} test"]
            case 'node':
                return ['npm test']
            case 'shell':
                return ConfigValidator.normalizeList(config.commands ?: config.testCommands)
            default:
                script.error "Unsupported language for testApp: ${language}"
        }
    }

    static List sonarCommands(Object script, Map config = [:]) {
        String language = detect(script, config)

        switch (language) {
            case 'maven':
                return ["${mavenCommand(script, config)} -B sonar:sonar"]
            case 'gradle':
                return ["${gradleCommand(script, config)} sonar"]
            case 'node':
            case 'shell':
                return ConfigValidator.normalizeList(config.commands ?: ['sonar-scanner'])
            default:
                script.error "Unsupported language for sonarScan: ${language}"
        }
    }

    private static String mavenCommand(Object script, Map config) {
        if (config.mavenCommand?.toString()?.trim()) {
            return config.mavenCommand as String
        }

        return script.fileExists('mvnw') ? './mvnw' : 'mvn'
    }

    private static String gradleCommand(Object script, Map config) {
        if (config.gradleCommand?.toString()?.trim()) {
            return config.gradleCommand as String
        }

        return script.fileExists('gradlew') ? './gradlew' : 'gradle'
    }
}
