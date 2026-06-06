def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Test App',
        language: 'auto',
        commands: null,
        junitPattern: '',
        allowEmptyResults: true
    ] + config

    ConfigValidator.requireUnix(this, 'testApp')

    stage(cfg.stageName as String) {
        List commands = cfg.commands
            ? ConfigValidator.normalizeList(cfg.commands)
            : LanguageStrategy.testCommands(this, cfg)

        runCommands(commands)

        if (cfg.junitPattern?.toString()?.trim()) {
            junit allowEmptyResults: (cfg.allowEmptyResults as boolean),
                testResults: cfg.junitPattern as String
        }
    }
}

private void runCommands(List commands) {
    if (!commands) {
        error 'testApp has no commands to run.'
    }

    commands.each { command ->
        sh command
    }
}
