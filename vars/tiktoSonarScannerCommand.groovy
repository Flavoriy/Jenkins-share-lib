def call(Map config = [:]) {
    List<String> args = [
        ConfigValidator.firstNonBlank(env.SONAR_SCANNER_COMMAND, config.sonarScannerCommand) ?: 'sonar-scanner'
    ]

    if (env.SONAR_PROJECT_KEY?.trim()) {
        args << scannerProperty('sonar.projectKey', env.SONAR_PROJECT_KEY)
    }

    if (env.SONAR_ORGANIZATION?.trim()) {
        args << scannerProperty('sonar.organization', env.SONAR_ORGANIZATION)
    }

    if (env.CHANGE_ID?.trim()) {
        args << scannerProperty('sonar.pullrequest.key', env.CHANGE_ID)
        args << scannerProperty('sonar.pullrequest.branch', env.CHANGE_BRANCH ?: env.BRANCH_NAME)
        args << scannerProperty('sonar.pullrequest.base', env.CHANGE_TARGET)
    } else {
        String branchName = (env.BRANCH_NAME ?: env.GIT_BRANCH ?: '').replaceFirst(/^origin\//, '').trim()
        args << scannerProperty('sonar.branch.name', branchName)
    }

    return args.join(' ')
}

private String scannerProperty(String name, String value) {
    return "-D${name}=${ConfigValidator.shellQuote(value ?: '')}"
}
