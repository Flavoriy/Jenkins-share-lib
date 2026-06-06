def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Update GitOps Manifest',
        push: true
    ] + config

    ConfigValidator.requireUnix(this, 'updateGitopsManifest')

    String imageRef = cfg.imageRef?.toString()?.trim() ?: env.IMAGE_REF
    ConfigValidator.requireValue(this, imageRef, 'imageRef')

    boolean changed = false
    stage(cfg.stageName as String) {
        changed = new GitopsUpdater(this).update(cfg + [imageRef: imageRef])
    }

    env.MANIFEST_CHANGED = changed.toString()
    return changed
}
