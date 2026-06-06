class ConfigValidator implements Serializable {
    static void requireUnix(Object script, String stepName) {
        if (!script.isUnix()) {
            script.error "${stepName} requires a Linux/Unix Jenkins agent."
        }
    }

    static void requireValue(Object script, Object value, String name) {
        if (!value?.toString()?.trim()) {
            script.error "Missing required config: ${name}"
        }
    }

    static void requireHttpsGitUrl(Object script, String value, String name) {
        requireValue(script, value, name)
        if (!value.startsWith('https://')) {
            script.error "${name} must be an https:// Git URL when using username/password credentials."
        }
    }

    static String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value?.toString()?.trim()) {
                return value.toString().trim()
            }
        }

        return ''
    }

    static String resolveImageTag(Object script, Map config = [:]) {
        if (config.imageTag?.toString()?.trim()) {
            return sanitizeDockerTag(config.imageTag as String)
        }

        String branch = script.env.BRANCH_NAME ?: script.env.GIT_BRANCH ?: 'local'
        String commit = script.env.GIT_COMMIT ?: script.sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
        String buildNumber = script.env.BUILD_NUMBER ?: '0'

        return sanitizeDockerTag("${branch}-${commit.take(12)}-${buildNumber}")
    }

    static String sanitizeDockerTag(String value) {
        return value
            .trim()
            .replaceAll(/^origin\//, '')
            .replaceAll(/[^A-Za-z0-9_.-]/, '-')
            .take(128)
    }

    static List normalizeList(Object value) {
        if (value == null) {
            return []
        }

        if (value instanceof CharSequence) {
            String item = value.toString().trim()
            return item ? [item] : []
        }

        if (value instanceof Iterable) {
            return value
                .findAll { it != null && it.toString().trim() }
                .collect { it.toString() }
        }

        String item = value.toString().trim()
        return item ? [item] : []
    }

    static String shellQuote(String value) {
        return "'${value.replace("'", "'\"'\"'")}'"
    }
}
