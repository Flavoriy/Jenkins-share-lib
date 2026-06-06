def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Clean Install and Build',
        language: 'auto',
        commands: null,
        skipTests: false
    ] + config

    ConfigValidator.requireUnix(this, 'buildApp')

    stage(cfg.stageName as String) {
        List commands = cfg.commands
            ? ConfigValidator.normalizeList(cfg.commands)
            : LanguageStrategy.buildCommands(this, cfg)

        runCommands(commands)
    }
}

private void runCommands(List commands) {
    if (!commands) {
        error 'buildApp has no commands to run.'
    }

    commands.each { command ->
        sh command
    }
}
