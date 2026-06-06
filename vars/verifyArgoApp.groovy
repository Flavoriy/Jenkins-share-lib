def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Verify ArgoCD App',
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

    stage(cfg.stageName as String) {
        Closure verify = {
            String args = argocdArgs(cfg)
            String refresh = (cfg.refresh as boolean) ? '--refresh' : ''
            String health = (cfg.waitHealth as boolean) ? '--health' : ''
            String sync = (cfg.waitSync as boolean) ? '--sync' : ''

            sh """
                set -e
                argocd app get '${cfg.appName}' ${args} ${refresh}
                argocd app wait '${cfg.appName}' ${args} ${health} ${sync} --timeout ${cfg.timeoutSeconds as int}
            """

            String imageRef = cfg.imageRef?.toString()?.trim() ?: env.IMAGE_REF
            if (imageRef) {
                sh """
                    set -e
                    argocd app manifests '${cfg.appName}' ${args} | grep -F '${imageRef}'
                """
            }
        }

        if (cfg.tokenCredentialsId?.toString()?.trim()) {
            withCredentials([string(credentialsId: cfg.tokenCredentialsId as String, variable: 'ARGOCD_AUTH_TOKEN')]) {
                verify.call()
            }
        } else {
            verify.call()
        }
    }
}

private String argocdArgs(Map cfg) {
    List args = []

    if (cfg.server?.toString()?.trim()) {
        args << "--server ${ConfigValidator.shellQuote(cfg.server as String)}"
    }

    if (cfg.tokenCredentialsId?.toString()?.trim()) {
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
