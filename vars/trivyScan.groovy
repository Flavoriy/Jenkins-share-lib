def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Trivy Scan',
        scanType: 'image',
        target: '',
        imageRef: '',
        severity: 'HIGH,CRITICAL',
        exitCode: 1,
        ignoreUnfixed: true,
        output: '',
        format: 'table'
    ] + config

    ConfigValidator.requireUnix(this, 'trivyScan')

    String target = cfg.target?.toString()?.trim()
    if (!target && cfg.scanType == 'image') {
        target = cfg.imageRef?.toString()?.trim() ?: env.IMAGE_REF
    }
    ConfigValidator.requireValue(this, target, 'target or imageRef')

    stage(cfg.stageName as String) {
        String ignoreUnfixed = (cfg.ignoreUnfixed as boolean) ? '--ignore-unfixed' : ''
        String output = cfg.output?.toString()?.trim()
            ? "--output ${ConfigValidator.shellQuote(cfg.output as String)}"
            : ''

        sh """
            set -e
            trivy '${cfg.scanType}' ${ignoreUnfixed} --exit-code ${cfg.exitCode as int} --severity '${cfg.severity}' --format '${cfg.format}' ${output} '${target}'
        """
    }
}
