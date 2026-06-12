def call(Map config = [:]) {
    Map cfg = [
        commands: []
    ] + config

    ConfigValidator.requireUnix(this, 'runShellCommands')

    List commands = ConfigValidator.normalizeList(cfg.commands)
    if (!commands) {
        error 'runShellCommands has no commands to run.'
    }

    commands.each { command ->
        sh command
    }
}
