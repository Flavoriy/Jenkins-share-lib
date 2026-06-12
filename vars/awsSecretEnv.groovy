def call(String secretId = '', Map config = [:]) {
    ConfigValidator.requireUnix(this, 'awsSecretEnv')

    String normalizedSecretId = secretId?.trim()
    if (!normalizedSecretId) {
        return []
    }

    String region = ConfigValidator.firstNonBlank(
        config.region,
        env.AWS_SECRETS_REGION,
        env.AWS_REGION
    ) ?: 'ap-southeast-1'

    String output = ''
    withEnv([
        "AWS_SECRET_ID=${normalizedSecretId}",
        "AWS_SECRET_REGION=${region}"
    ]) {
        output = sh(
            script: '''
                set +x
                command -v aws >/dev/null
                command -v jq >/dev/null
                aws secretsmanager get-secret-value \
                  --region "$AWS_SECRET_REGION" \
                  --secret-id "$AWS_SECRET_ID" \
                  --query SecretString \
                  --output text \
                  | jq -r 'if type == "string" then fromjson else . end | to_entries[] | .key + "=" + (.value | tostring)'
            ''',
            returnStdout: true
        ).trim()
    }

    return output ? output.readLines().findAll { it.trim() } : []
}
