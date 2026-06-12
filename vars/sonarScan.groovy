def call(Map config = [:]) {
    Map cfg = [
        stageName: 'SonarQube Scan',
        wrapStage: true,
        language: 'auto',
        sonarQubeEnv: 'SonarQube',
        useSonarQubeEnv: true,
        sonarHostUrl: '',
        commands: null,
        qualityGateEnabled: false,
        qualityGateStageName: 'SonarQube Quality Gate',
        abortPipeline: true,
        qualityGateTimeoutMinutes: 5
    ] + config

    ConfigValidator.requireUnix(this, 'sonarScan')

    Closure body = {
        List commands = cfg.commands
            ? ConfigValidator.normalizeList(cfg.commands)
            : LanguageStrategy.sonarCommands(this, cfg)

        if (env.SONAR_TOKEN?.trim()) {
            List sonarEnv = []
            String hostUrl = cfg.sonarHostUrl?.toString()?.trim() ?: env.SONAR_HOST_URL
            if (hostUrl?.trim()) {
                sonarEnv << "SONAR_HOST_URL=${hostUrl}"
            }

            withEnv(sonarEnv) {
                runCommands(commands)
            }
        } else if (cfg.useSonarQubeEnv as boolean) {
            ConfigValidator.requireValue(this, cfg.sonarQubeEnv, 'sonarQubeEnv')
            withSonarQubeEnv(cfg.sonarQubeEnv as String) {
                runCommands(commands)
            }
        } else {
            runCommands(commands)
        }
    }

    runWithOptionalStage(cfg, body)

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

private void runWithOptionalStage(Map cfg, Closure body) {
    if (cfg.wrapStage == null || cfg.wrapStage.toString().toBoolean()) {
        stage(cfg.stageName as String) {
            body.call()
        }
        return
    }

    body.call()
}
