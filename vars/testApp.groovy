def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Test App',
        wrapStage: true,
        language: 'auto',
        commands: null,
        junitPattern: '',
        allowEmptyResults: true
    ] + config

    ConfigValidator.requireUnix(this, 'testApp')

    Closure body = {
        List commands = cfg.commands
            ? ConfigValidator.normalizeList(cfg.commands)
            : LanguageStrategy.testCommands(this, cfg)

        runCommands(commands)

        if (cfg.junitPattern?.toString()?.trim()) {
            junit allowEmptyResults: (cfg.allowEmptyResults as boolean),
                testResults: cfg.junitPattern as String
        }
    }

    runWithOptionalStage(cfg, body)
}

private void runCommands(List commands) {
    if (!commands) {
        error 'testApp has no commands to run.'
    }

    commands.each { command ->
        sh command
    }
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
