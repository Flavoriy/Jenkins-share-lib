def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Clean Install and Build',
        wrapStage: true,
        language: 'auto',
        commands: null,
        skipTests: false
    ] + config

    ConfigValidator.requireUnix(this, 'buildApp')

    Closure body = {
        List commands = cfg.commands
            ? ConfigValidator.normalizeList(cfg.commands)
            : LanguageStrategy.buildCommands(this, cfg)

        runCommands(commands)
    }

    runWithOptionalStage(cfg, body)
}

private void runCommands(List commands) {
    if (!commands) {
        error 'buildApp has no commands to run.'
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
