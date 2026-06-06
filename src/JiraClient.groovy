import groovy.json.JsonOutput

class JiraClient implements Serializable {
    private final Object script

    JiraClient(Object script) {
        this.script = script
    }

    void notify(Map config = [:]) {
        String baseUrl = ConfigValidator.firstNonBlank(config.baseUrl)
        String credentialsId = ConfigValidator.firstNonBlank(config.credentialsId)
        String issueKey = ConfigValidator.firstNonBlank(config.issueKey)
        String message = ConfigValidator.firstNonBlank(config.message)
        String transitionId = ConfigValidator.firstNonBlank(config.transitionId)
        String apiVersion = ConfigValidator.firstNonBlank(config.apiVersion) ?: '2'

        ConfigValidator.requireValue(script, baseUrl, 'baseUrl')
        ConfigValidator.requireValue(script, credentialsId, 'credentialsId')
        ConfigValidator.requireValue(script, issueKey, 'issueKey')
        ConfigValidator.requireValue(script, message, 'message')

        addComment(baseUrl, credentialsId, issueKey, message, apiVersion)

        if (transitionId) {
            transition(baseUrl, credentialsId, issueKey, transitionId, apiVersion)
        }
    }

    private void addComment(String baseUrl, String credentialsId, String issueKey, String message, String apiVersion) {
        String payloadFile = ".jira-comment-${issueKey}.json"
        script.writeFile file: payloadFile, text: JsonOutput.toJson([body: message])

        runCurl(credentialsId, [
            "JIRA_BASE_URL=${baseUrl.replaceAll(/\/+$/, '')}",
            "JIRA_ISSUE=${issueKey}",
            "JIRA_API_VERSION=${apiVersion}",
            "JIRA_PAYLOAD=${payloadFile}"
        ], '''
            curl -sS -f \
              -u "$JIRA_USER:$JIRA_TOKEN" \
              -H "Content-Type: application/json" \
              --data @"$JIRA_PAYLOAD" \
              "$JIRA_BASE_URL/rest/api/$JIRA_API_VERSION/issue/$JIRA_ISSUE/comment"
        ''')
    }

    private void transition(String baseUrl, String credentialsId, String issueKey, String transitionId, String apiVersion) {
        String payloadFile = ".jira-transition-${issueKey}.json"
        script.writeFile file: payloadFile, text: JsonOutput.toJson([transition: [id: transitionId]])

        runCurl(credentialsId, [
            "JIRA_BASE_URL=${baseUrl.replaceAll(/\/+$/, '')}",
            "JIRA_ISSUE=${issueKey}",
            "JIRA_API_VERSION=${apiVersion}",
            "JIRA_PAYLOAD=${payloadFile}"
        ], '''
            curl -sS -f \
              -u "$JIRA_USER:$JIRA_TOKEN" \
              -H "Content-Type: application/json" \
              --data @"$JIRA_PAYLOAD" \
              "$JIRA_BASE_URL/rest/api/$JIRA_API_VERSION/issue/$JIRA_ISSUE/transitions"
        ''')
    }

    private void runCurl(String credentialsId, List envVars, String command) {
        script.withCredentials([script.usernamePassword(credentialsId: credentialsId, usernameVariable: 'JIRA_USER', passwordVariable: 'JIRA_TOKEN')]) {
            script.withEnv(envVars) {
                script.sh """
                    set -e
                    set +x
                    ${command}
                """
            }
        }
    }
}
