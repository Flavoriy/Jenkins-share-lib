def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Verify ArgoCD App',
        wrapStage: true,
        appName: '',
        server: '',
        tokenCredentialsId: '',
        grpcWeb: true,
        insecure: false,
        refresh: true,
        waitHealth: true,
        waitSync: true,
        timeoutSeconds: 300,
        imageRef: ''
    ] + config

    ConfigValidator.requireUnix(this, 'verifyArgoApp')
    ConfigValidator.requireValue(this, cfg.appName, 'appName')

    Closure body = {
        Closure verify = {
            String args = argocdArgs(cfg)
            String refresh = (cfg.refresh as boolean) ? '--refresh' : ''
            String health = (cfg.waitHealth as boolean) ? '--health' : ''
            String sync = (cfg.waitSync as boolean) ? '--sync' : ''

            sh """
                set -e
                set +x
                argocd app get '${cfg.appName}' ${args} ${refresh}
                argocd app wait '${cfg.appName}' ${args} ${health} ${sync} --timeout ${cfg.timeoutSeconds as int}
            """

            String imageRef = cfg.imageRef?.toString()?.trim() ?: env.IMAGE_REF
            if (imageRef) {
                sh """
                    set -e
                    set +x
                    argocd app manifests '${cfg.appName}' ${args} | grep -F '${imageRef}'
                """
            }
        }

        if (env.ARGOCD_AUTH_TOKEN?.trim() || env.ARGOCD_TOKEN?.trim()) {
            withEnv(["ARGOCD_AUTH_TOKEN=${env.ARGOCD_AUTH_TOKEN ?: env.ARGOCD_TOKEN}"]) {
                verify.call()
            }
        } else if (cfg.tokenCredentialsId?.toString()?.trim()) {
            withCredentials([string(credentialsId: cfg.tokenCredentialsId as String, variable: 'ARGOCD_AUTH_TOKEN')]) {
                verify.call()
            }
        } else {
            verify.call()
        }
    }

    runWithOptionalStage(cfg, body)
}

private String argocdArgs(Map cfg) {
    List args = []

    if (cfg.server?.toString()?.trim()) {
        args << "--server ${ConfigValidator.shellQuote(cfg.server as String)}"
    }

    boolean hasEnvToken = env.ARGOCD_AUTH_TOKEN?.trim() || env.ARGOCD_TOKEN?.trim()
    if (cfg.tokenCredentialsId?.toString()?.trim() || cfg.token?.toString()?.trim() || cfg.authToken?.toString()?.trim() || hasEnvToken) {
        args << '--auth-token "$ARGOCD_AUTH_TOKEN"'
    }

    if (cfg.grpcWeb as boolean) {
        args << '--grpc-web'
    }

    if (cfg.insecure as boolean) {
        args << '--insecure'
    }

    return args.join(' ')
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
