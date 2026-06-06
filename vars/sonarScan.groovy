def call(Map config = [:]) {
    Map cfg = [
        stageName: 'SonarQube Scan',
        language: 'auto',
        sonarQubeEnv: 'SonarQube',
        commands: null,
        qualityGateEnabled: false,
        qualityGateStageName: 'SonarQube Quality Gate',
        abortPipeline: true,
        qualityGateTimeoutMinutes: 5
    ] + config

    ConfigValidator.requireUnix(this, 'sonarScan')
    ConfigValidator.requireValue(this, cfg.sonarQubeEnv, 'sonarQubeEnv')

    stage(cfg.stageName as String) {
        List commands = cfg.commands
            ? ConfigValidator.normalizeList(cfg.commands)
            : LanguageStrategy.sonarCommands(this, cfg)

        withSonarQubeEnv(cfg.sonarQubeEnv as String) {
            runCommands(commands)
        }
    }

    if (cfg.qualityGateEnabled as boolean) {
        stage(cfg.qualityGateStageName as String) {
            timeout(time: (cfg.qualityGateTimeoutMinutes as int), unit: 'MINUTES') {
                waitForQualityGate abortPipeline: (cfg.abortPipeline as boolean)
            }
        }
    }
}

private void runCommands(List commands) {
    if (!commands) {
        error 'sonarScan has no commands to run.'
    }

    commands.each { command ->
        sh command
    }
}
