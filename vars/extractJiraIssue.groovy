def call(Object source = null, Map config = [:]) {
    Map cfg = config ?: [:]
    Object input = source

    if (source instanceof Map && !config) {
        cfg = source as Map
        input = cfg.source
    }

    String pattern = cfg.pattern?.toString()?.trim() ?: '[A-Z][A-Z0-9]+-[0-9]+'
    if (cfg.projectKey?.toString()?.trim()) {
        pattern = "${cfg.projectKey.toString().toUpperCase()}-[0-9]+"
    }

    List sources = []
    if (input) {
        sources << input.toString()
    }

    [
        env.CHANGE_BRANCH,
        env.BRANCH_NAME,
        env.GIT_BRANCH,
        env.CHANGE_TITLE
    ].findAll { it }.each { sources << it.toString() }

    if (cfg.includeCommitMessage != false) {
        try {
            sources << sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
        } catch (ignored) {
            echo 'Cannot read git commit message while extracting Jira issue.'
        }
    }

    String issue = findIssue(sources, pattern)
    env.JIRA_ISSUE = issue ?: ''

    if (!issue && (cfg.required as boolean)) {
        error "No Jira issue found with pattern: ${pattern}"
    }

    return issue ?: ''
}

private String findIssue(List sources, String pattern) {
    for (String source : sources) {
        def matcher = source =~ pattern
        if (matcher.find()) {
            return matcher.group(0).toUpperCase()
        }
    }

    return ''
}
