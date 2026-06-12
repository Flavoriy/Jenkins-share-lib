# Tài liệu Jenkins Shared Library

Tài liệu này giải thích từng file trong Jenkins Shared Library và cách dùng các step trong Jenkinsfile.

## Tổng quan

Project này tách CI/CD pipeline thành nhiều global step nhỏ trong `vars/`. Mỗi file `.groovy` trong `vars/` sẽ trở thành một hàm có thể gọi trực tiếp từ Jenkinsfile.

Logic xử lý phức tạp hơn nằm trong `src/`. Jenkinsfile không cần gọi trực tiếp các class trong `src/`; các step trong `vars/` sẽ dùng chung các helper class này.

## Cấu trúc thư mục

```text
vars/
  awsSecretEnv.groovy
  buildApp.groovy
  dockerBuild.groovy
  dockerPush.groovy
  prepareDockerImageRefs.groovy
  runShellCommands.groovy
  sonarScan.groovy
  testApp.groovy
  tiktoAppSecretId.groovy
  tiktoBuildContext.groovy
  tiktoCommonCiEnv.groovy
  tiktoDockerBuildArgs.groovy
  tiktoSharedSecretId.groovy
  tiktoSonarScannerCommand.groovy
  trivyScan.groovy
  updateGitopsManifest.groovy
  verifyArgoApp.groovy
  withTiktoCiEnv.groovy

src/
  LanguageStrategy.groovy
  GitopsUpdater.groovy
  ConfigValidator.groovy
```

## Flow CI/CD chính

Flow đề xuất cho App 1/TikTo:

```text
checkout source
build app
test app
SonarQube scan
Docker build image
Trivy scan image
Docker push image to GHCR
update GitOps manifest
verify ArgoCD app
```

GHCR chỉ lưu Docker/container image, không lưu container đang chạy. Kubernetes hoặc ArgoCD sẽ pull image từ GHCR về để tạo Pod/container.

## Jenkinsfile gọn nhưng vẫn khai báo stage

Jenkinsfile vẫn khai báo `pipeline` và từng `stage` để Jenkins UI hiển thị rõ flow. Logic bên trong stage gọi shared-library step với `wrapStage: false` để không tạo nested stage.

Ví dụ:

```groovy
@Library('jenkins-share-lib@main') _

pipeline {
    agent { label 'agent' }
    stages {
        stage('Build App') {
            steps {
                script {
                    buildApp(
                        wrapStage: false,
                        language: 'shell',
                        commands: ['npm run build']
                    )
                }
            }
        }
    }
}
```

Các step như `buildApp`, `testApp`, `sonarScan`, `dockerBuild`, `dockerPush`, `trivyScan`, `updateGitopsManifest`, `verifyArgoApp` vẫn tự tạo stage như cũ nếu không truyền `wrapStage: false`.

## Giải thích file trong `vars/`

### `awsSecretEnv.groovy`

Load secret JSON từ AWS Secrets Manager và trả về list env dạng `KEY=value`.

### `runShellCommands.groovy`

Chạy một list shell command đơn giản. Dùng cho stage không cần build/test semantic riêng, ví dụ static checks hoặc `docker compose`.

### `prepareDockerImageRefs.groovy`

Tạo `IMAGE_REF`, optional extra image refs và set các biến `env.IMAGE_REPOSITORY`, `env.IMAGE_TAG`, `env.IMAGE_REF`.

### `tikto*.groovy` helpers

Các helper riêng cho App 1/TikTo: xác định branch/deploy env, common CI env, secret id, Docker build args và Sonar scanner command.

### `buildApp.groovy`

Dùng để build source code của app.

Step này có thể tự detect project type:

- Maven: có `pom.xml`
- Gradle: có `build.gradle` hoặc `build.gradle.kts`
- Node.js: có `package.json`
- Shell/custom: truyền command riêng

Ví dụ:

```groovy
buildApp(language: 'maven', skipTests: true)
```

Input quan trọng:

- `language`: `auto`, `maven`, `gradle`, `node`, `shell`
- `commands`: list command custom nếu không muốn auto detect
- `skipTests`: với Maven, thêm `-DskipTests`
- `stageName`: tên stage Jenkins

Output:

- Không return giá trị.
- Nếu command build fail thì pipeline fail.

### `testApp.groovy`

Dùng để chạy test và publish test report.

Ví dụ:

```groovy
testApp(
    language: 'maven',
    junitPattern: '**/target/surefire-reports/*.xml'
)
```

Input quan trọng:

- `language`: `auto`, `maven`, `gradle`, `node`, `shell`
- `commands`: command test custom
- `junitPattern`: pattern file JUnit XML
- `allowEmptyResults`: cho phép không có report hay không

Output:

- Publish JUnit report nếu có `junitPattern`.
- Nếu test fail thì pipeline fail.

### `sonarScan.groovy`

Dùng để scan source code với SonarQube.

Ví dụ:

```groovy
sonarScan(
    language: 'maven',
    sonarQubeEnv: 'SonarQube',
    qualityGateEnabled: true
)
```

Input quan trọng:

- `sonarQubeEnv`: tên SonarQube server đã config trong Jenkins
- `commands`: command scan custom
- `qualityGateEnabled`: nếu `true`, Jenkins đợi quality gate
- `qualityGateTimeoutMinutes`: timeout khi đợi quality gate

Output:

- Đẩy scan result lên SonarQube.
- Nếu quality gate fail và `abortPipeline: true`, pipeline fail.

### `dockerBuild.groovy`

Dùng để build Docker image local trên Jenkins agent.

Ví dụ:

```groovy
def imageRef = dockerBuild(
    imageRepository: 'ghcr.io/my-org/my-app',
    dockerfile: 'Dockerfile',
    context: '.'
)
```

Input quan trọng:

- `imageRepository`: repo image, ví dụ `ghcr.io/my-org/my-app`
- `imageTag`: tag custom. Nếu để trống, library tạo tag từ branch, commit, build number
- `dockerfile`: path Dockerfile
- `context`: Docker build context
- `buildArgs`: Docker build args, có thể là map hoặc list string

Output:

- Return `imageRef`, ví dụ `ghcr.io/my-org/my-app:main-a1b2c3-12`
- Set env:
  - `env.IMAGE_REPOSITORY`
  - `env.IMAGE_TAG`
  - `env.IMAGE_REF`

### `trivyScan.groovy`

Dùng để scan bảo mật bằng Trivy.

Mặc định step này scan Docker image từ `imageRef` hoặc `env.IMAGE_REF`.

Ví dụ:

```groovy
trivyScan(imageRef: imageRef)
```

Input quan trọng:

- `scanType`: mặc định `image`
- `imageRef`: image cần scan
- `target`: target custom nếu không scan image
- `severity`: mặc định `HIGH,CRITICAL`
- `exitCode`: mặc định `1`, có vulnerability thì fail pipeline
- `ignoreUnfixed`: bỏ qua lỗi chưa có fix
- `format`, `output`: format và file output của Trivy

Output:

- Nếu Trivy detect vulnerability theo rule và `exitCode: 1`, pipeline fail.

### `dockerPush.groovy`

Dùng để login registry và push Docker image.

Step này không build image. Image phải được build trước bằng `dockerBuild`.

Ví dụ:

```groovy
dockerPush(
    imageRef: imageRef,
    credentialsId: 'ghcr-token'
)
```

Input quan trọng:

- `imageRef`: image cần push
- `registry`: mặc định `ghcr.io`
- `credentialsId`: Jenkins credential kiểu username/password

Output:

- Push image lên registry.
- Return `imageRef`.

### `updateGitopsManifest.groovy`

Dùng để update Kubernetes/GitOps manifest sau khi có image mới.

Step này sẽ:

1. Clone manifest repo.
2. Update dòng `image:` trong manifest file.
3. Commit thay đổi.
4. Push lên manifest branch.

Ví dụ:

```groovy
updateGitopsManifest(
    manifestRepoUrl: 'https://github.com/my-org/k8s-manifests.git',
    manifestBranch: 'main',
    manifestFile: 'apps/my-app/deployment.yaml',
    manifestGitCredentialsId: 'github-token',
    imageRef: imageRef
)
```

Input quan trọng:

- `manifestRepoUrl`: HTTPS URL của manifest repo
- `manifestBranch`: branch cần update
- `manifestFile`: file YAML cần update
- `manifestGitCredentialsId`: credential để clone/push manifest repo
- `imageRef`: image mới
- `manifestUpdateCommands`: command custom nếu logic `sed` mặc định không phù hợp

Output:

- Return `true` nếu manifest có thay đổi.
- Set `env.MANIFEST_CHANGED`.

### `verifyArgoApp.groovy`

Dùng để verify ArgoCD app sau khi manifest repo đã được update.

Step này chạy:

- `argocd app get`
- `argocd app wait`
- optional check manifest có đúng `imageRef`

Ví dụ:

```groovy
verifyArgoApp(
    appName: 'my-app',
    server: 'argocd.example.com',
    tokenCredentialsId: 'argocd-token',
    imageRef: imageRef
)
```

Input quan trọng:

- `appName`: tên ArgoCD application
- `server`: ArgoCD server
- `tokenCredentialsId`: Jenkins secret text credential cho ArgoCD token
- `timeoutSeconds`: timeout khi wait app
- `waitHealth`, `waitSync`: bật/tắt wait health/sync
- `imageRef`: image cần verify trong manifest

Output:

- Nếu ArgoCD app không sync/healthy trong timeout, pipeline fail.

## Giải thích file trong `src/`

### `LanguageStrategy.groovy`

Helper detect ngôn ngữ/project type và trả về command build/test/sonar.

Dùng bởi:

- `buildApp.groovy`
- `testApp.groovy`
- `sonarScan.groovy`

Trách nhiệm:

- Detect `pom.xml` -> Maven
- Detect `build.gradle` hoặc `build.gradle.kts` -> Gradle
- Detect `package.json` -> Node.js
- Chọn command default phù hợp

### `ConfigValidator.groovy`

Helper validate config và xử lý input chung.

Dùng bởi hầu hết các step.

Trách nhiệm:

- Check agent có phải Linux/Unix không
- Check required config
- Check Git URL HTTPS
- Tạo Docker image tag
- Sanitize Docker tag
- Chuẩn hóa string/list command
- Quote shell string

### `GitopsUpdater.groovy`

Helper xử lý GitOps manifest repo.

Dùng bởi:

- `updateGitopsManifest.groovy`

Trách nhiệm:

- Clone manifest repo
- Update image trong manifest file
- Commit thay đổi
- Push lên branch manifest
- Set `env.MANIFEST_CHANGED`

## Ví dụ Jenkinsfile đầy đủ

```groovy
@Library('jenkins-share-lib') _

node('linux') {
    checkout scm

    buildApp(language: 'maven', skipTests: true)
    testApp(language: 'maven', junitPattern: '**/target/surefire-reports/*.xml')
    sonarScan(language: 'maven', sonarQubeEnv: 'SonarQube', qualityGateEnabled: true)

    def imageRef = dockerBuild(
        imageRepository: 'ghcr.io/my-org/my-app',
        dockerfile: 'Dockerfile',
        context: '.'
    )

    trivyScan(imageRef: imageRef)

    dockerPush(
        imageRef: imageRef,
        credentialsId: 'ghcr-token'
    )

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
}
```

## Jenkins credentials cần tạo

- `ghcr-token`: username/password credential để push image lên GHCR.
- `github-token`: username/password credential để clone/push manifest repo.
- `argocd-token`: secret text credential để verify ArgoCD app.

## Tool cần có trên Jenkins agent

Agent Linux nên có các tool sau:

- `git`
- `docker`
- `trivy`
- `mvn` hoặc `./mvnw`
- `gradle` hoặc `./gradlew` nếu build Gradle
- `npm` nếu build Node.js
- `argocd` nếu dùng `verifyArgoApp`
