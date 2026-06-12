def call(Map config = [:]) {
    List<String> deployBranches = ConfigValidator.normalizeList(config.deployBranches ?: ['main', 'dev'])
    String branchName = normalizeBranchName(env.BRANCH_NAME ?: env.GIT_BRANCH ?: '')
    String changeTarget = normalizeBranchName(env.CHANGE_TARGET ?: '')
    boolean pullRequestBuild = env.CHANGE_ID?.trim() ? true : false
    boolean supportedPullRequest = pullRequestBuild && deployBranches.contains(changeTarget)
    boolean deployBranchBuild = !pullRequestBuild && deployBranches.contains(branchName)
    boolean shouldRun = supportedPullRequest || deployBranchBuild
    String deployEnv = deployBranchBuild ? resolveDeployEnv(config, branchName) : ''

    return [
        deployBranches: deployBranches,
        branchName: branchName,
        changeTarget: changeTarget,
        pullRequestBuild: pullRequestBuild,
        supportedPullRequest: supportedPullRequest,
        deployBranchBuild: deployBranchBuild,
        shouldRun: shouldRun,
        deployEnv: deployEnv,
        manifestFile: deployEnv ? resolveManifestFile(config, deployEnv) : '',
        argoAppName: deployEnv ? resolveArgoAppName(config, deployEnv) : ''
    ]
}

private String resolveDeployEnv(Map config, String branchName) {
    Map branchEnvironments = (config.branchEnvironments ?: [main: 'prod', dev: 'dev']) as Map
    String deployEnv = branchEnvironments[branchName]?.toString()?.trim()
    return deployEnv ?: branchName
}

private String resolveManifestFile(Map config, String deployEnv) {
    String explicit = ConfigValidator.firstNonBlank(env.GITOPS_MANIFEST_FILE, config.gitopsManifestFile)
    if (explicit) {
        return explicit
    }

    String pattern = ConfigValidator.firstNonBlank(config.gitopsManifestPattern) ?: 'apps/tikto/overlays/%s/patch-image.yaml'
    return String.format(pattern, deployEnv)
}

private String resolveArgoAppName(Map config, String deployEnv) {
    String explicit = ConfigValidator.firstNonBlank(env.ARGOCD_APP_NAME, config.argocdAppName)
    if (explicit) {
        return explicit
    }

    String pattern = ConfigValidator.firstNonBlank(config.argocdAppPattern) ?: 'tikto-%s'
    return String.format(pattern, deployEnv)
}

private String normalizeBranchName(String branchName) {
    return branchName.replaceFirst(/^origin\//, '').trim()
}
