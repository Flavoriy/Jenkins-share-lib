def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Update GitOps Manifest',
        wrapStage: true,
        push: true
    ] + config

    ConfigValidator.requireUnix(this, 'updateGitopsManifest')

    String imageRef = cfg.imageRef?.toString()?.trim() ?: env.IMAGE_REF
    ConfigValidator.requireValue(this, imageRef, 'imageRef')

    boolean changed = false
    Closure body = {
        changed = new GitopsUpdater(this).update(cfg + [imageRef: imageRef])
    }

    runWithOptionalStage(cfg, body)

    env.MANIFEST_CHANGED = changed.toString()
    return changed
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
