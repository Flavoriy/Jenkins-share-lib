

## Install Jenkins

Jenkins server

```bash
sudo apt update
sudo apt install fontconfig openjdk-21-jre
java -version
```

```bash
sudo wget -O /etc/apt/keyrings/jenkins-keyring.asc \
  https://pkg.jenkins.io/debian-stable/jenkins.io-2026.key
echo "deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc]" \
  https://pkg.jenkins.io/debian-stable binary/ | sudo tee \
  /etc/apt/sources.list.d/jenkins.list > /dev/null
sudo apt update
sudo apt install jenkins
```
```
sudo systemctl enable jenkins
sudo systemctl start jenkins
sudo systemctl status jenkins
```

## Jenkins Shared Library

Project nay la Jenkins Shared Library cho CI/CD va GitOps. Cac step trong `vars/` co the goi truc tiep tu Jenkinsfile.

### Cau truc file

```text
vars/
  buildApp.groovy
  testApp.groovy
  dockerBuild.groovy
  dockerPush.groovy
  sonarScan.groovy
  trivyScan.groovy
  updateGitopsManifest.groovy
  verifyArgoApp.groovy
  jiraNotify.groovy
  extractJiraIssue.groovy

src/
  LanguageStrategy.groovy
  JiraClient.groovy
  GitopsUpdater.groovy
  ConfigValidator.groovy
```

### Giai thich tung file

#### `vars/`

`vars/` chua cac global DSL step. Moi file trong folder nay se thanh mot ham goi truc tiep duoc trong Jenkinsfile.

- `buildApp.groovy`: build source code cua app. Step nay tu detect Maven, Gradle, Node thong qua `LanguageStrategy`, hoac nhan `commands` custom. Vi du Maven mac dinh chay `mvn clean install`; co the dung `skipTests: true` neu muon tach test ra step rieng.
- `testApp.groovy`: chay test cua app. Step nay cung detect Maven, Gradle, Node hoac nhan `commands` custom. Neu truyen `junitPattern`, Jenkins se publish test report bang `junit`.
- `sonarScan.groovy`: scan code voi SonarQube. Step nay boc command scan trong `withSonarQubeEnv(...)`; co the bat `qualityGateEnabled: true` de doi quality gate truoc khi pipeline di tiep.
- `dockerBuild.groovy`: build Docker image local tren Jenkins agent. Input chinh la `imageRepository`, `dockerfile`, `context`, `buildArgs`. Step nay tao `imageRef`, set `env.IMAGE_REF`, `env.IMAGE_TAG`, va return `imageRef`.
- `trivyScan.groovy`: scan bao mat bang Trivy. Mac dinh scan Docker image tu `imageRef` hoac `env.IMAGE_REF`; co the doi `severity`, `exitCode`, `ignoreUnfixed`, `format`, `output`.
- `dockerPush.groovy`: login registry va push Docker image. Mac dinh registry la `ghcr.io`; can `credentialsId` kieu username/password. Step nay chi push image da build, khong build lai.
- `updateGitopsManifest.groovy`: update manifest repo theo GitOps. Step nay goi `GitopsUpdater`, clone repo manifest, update image moi vao file YAML, commit va push len branch manifest.
- `verifyArgoApp.groovy`: verify app tren ArgoCD sau khi manifest da duoc push. Step nay dung `argocd app get`, `argocd app wait`, va co the kiem tra manifest da co dung `imageRef`.
- `extractJiraIssue.groovy`: lay Jira issue key tu branch name, PR title hoac commit message. Vi du branch `feature/OPS-123-login` se tra ve `OPS-123` va set `env.JIRA_ISSUE`.
- `jiraNotify.groovy`: comment vao Jira issue sau khi pipeline chay. Step nay goi `JiraClient`; co the comment ket qua build va optional transition issue bang `transitionId`.

#### `src/`

`src/` chua helper class Groovy. Jenkinsfile khong goi truc tiep cac file nay; cac step trong `vars/` se import va dung logic ben trong.

- `LanguageStrategy.groovy`: detect ngon ngu/project type va tra ve command phu hop cho build, test, sonar. Hien ho tro Maven, Gradle, Node va shell custom.
- `ConfigValidator.groovy`: helper validate config va chuan hoa input. File nay gom cac ham nhu check Linux agent, check required value, tao Docker tag, normalize list command, quote shell string.
- `GitopsUpdater.groovy`: logic clone manifest repo, update dong image, commit va push thay doi. File nay duoc dung boi `updateGitopsManifest.groovy`.
- `JiraClient.groovy`: logic goi Jira REST API bang `curl`. File nay them comment vao issue va optional transition status, duoc dung boi `jiraNotify.groovy`.

### Cau hinh tren Jenkins

Vao `Manage Jenkins` -> `System` -> `Global Trusted Pipeline Libraries`, them library:

- Name: `jenkins-share-lib`
- Default version: branch chinh cua repo, vi du `main`
- Retrieval method: Git
- Project repository: URL repo nay

### Credentials can co

- `ghcr-token`: Jenkins credential kieu `Username with password`; username la GitHub username, password la GitHub token co quyen push package len GHCR.
- `github-token`: Jenkins credential kieu `Username with password`; username la GitHub username, password la GitHub token co quyen push manifest repo.
- `jira-token`: Jenkins credential kieu `Username with password`; username la Jira user/email, password la Jira token.
- `argocd-token`: Jenkins credential kieu `Secret text` neu muon verify bang ArgoCD token.
- SonarQube server da khai bao trong Jenkins voi name mac dinh `SonarQube`.

### Vi du Jenkinsfile

```groovy
@Library('jenkins-share-lib') _

node('linux') {
    checkout scm

    def jiraIssue = extractJiraIssue(required: false)

    buildApp(language: 'maven', skipTests: true)
    testApp(language: 'maven', junitPattern: '**/target/surefire-reports/*.xml')
    sonarScan(language: 'maven', sonarQubeEnv: 'SonarQube', qualityGateEnabled: true)

    def imageRef = dockerBuild(
        imageRepository: 'ghcr.io/my-org/my-app',
        dockerfile: 'Dockerfile',
        context: '.'
    )

    trivyScan(imageRef: imageRef)

    dockerPush(imageRef: imageRef, credentialsId: 'ghcr-token')

    updateGitopsManifest(
        manifestRepoUrl: 'https://github.com/my-org/k8s-manifests.git',
        manifestBranch: 'main',
        manifestFile: 'apps/my-app/deployment.yaml',
        manifestGitCredentialsId: 'github-token',
        imageRef: imageRef
    )

    verifyArgoApp(
        appName: 'my-app',
        server: 'argocd.example.com',
        tokenCredentialsId: 'argocd-token',
        imageRef: imageRef
    )

    jiraNotify(
        enabled: jiraIssue != '',
        baseUrl: 'https://my-org.atlassian.net',
        credentialsId: 'jira-token',
        issueKey: jiraIssue
    )
}
```

### Cac step chinh

- `buildApp`: detect Maven/Gradle/Node hoac chay command custom de build app.
- `testApp`: chay test va publish JUnit neu co `junitPattern`.
- `sonarScan`: chay SonarQube scan va optional quality gate.
- `dockerBuild`: build Docker image va return `imageRef`.
- `dockerPush`: login registry va push Docker image.
- `trivyScan`: scan image hoac filesystem bang Trivy.
- `updateGitopsManifest`: clone manifest repo, update image, commit va push.
- `verifyArgoApp`: verify ArgoCD app sync/health va optional imageRef.
- `extractJiraIssue`: lay Jira issue tu branch name, PR title hoac commit message.
- `jiraNotify`: comment Jira issue va optional transition.
