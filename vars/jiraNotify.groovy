def call(Map config = [:]) {
    Map cfg = [
        stageName: 'Jira Notify',
        enabled: true,
        baseUrl: '',
        credentialsId: '',
        issueKey: '',
        message: '',
        transitionId: '',
        apiVersion: '2'
    ] + config

    if (!(cfg.enabled as boolean)) {
        echo 'Jira notify skipped.'
        return
    }

    ConfigValidator.requireUnix(this, 'jiraNotify')

    stage(cfg.stageName as String) {
        String issueKey = cfg.issueKey?.toString()?.trim() ?: env.JIRA_ISSUE
        if (!issueKey) {
            issueKey = extractJiraIssue(required: false)
        }

        if (!issueKey) {
            echo 'Jira notify skipped because no Jira issue was found.'
            return
        }

        String message = cfg.message?.toString()?.trim() ?: defaultMessage()
        new JiraClient(this).notify(cfg + [issueKey: issueKey, message: message])
    }
}

private String defaultMessage() {
    String result = currentBuild.currentResult ?: currentBuild.result ?: 'SUCCESS'
    String jobName = env.JOB_NAME ?: 'jenkins-job'
    String buildNumber = env.BUILD_NUMBER ?: ''
    String buildUrl = env.BUILD_URL ?: ''

    return "Jenkins build ${jobName} #${buildNumber} finished with ${result}. ${buildUrl}".trim()
}
