# Publishing Comet to Maven Central

This guide covers the full flow for publishing Comet to Maven Central. Once published, [klibs.io](https://klibs.io) auto-indexes KMP libraries from Maven Central within 24 hours.

## Prerequisites

- Maven Central (Sonatype) account
- GPG signing key
- GitHub repository is public
- Gradle 8+

## 1. Maven Central Setup

1. Register at [central.sonatype.com](https://central.sonatype.com)
2. Create a namespace for `io.github.pandubaraja`
3. Verify ownership via DNS TXT record or GitHub proof

## 2. GPG Key Setup

Generate a GPG key for signing artifacts:

```bash
gpg --full-generate-key
```

Choose RSA 4096-bit, set a passphrase, then upload to a keyserver:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

Export the private key for CI use:

```bash
gpg --export-secret-keys <KEY_ID> | base64
```

## 3. Local Credentials

Add credentials to `~/.gradle/gradle.properties` (never commit this file):

```properties
mavenCentralUsername=<sonatype-username>
mavenCentralPassword=<sonatype-password>
signing.keyId=<last-8-chars-of-key>
signing.password=<gpg-passphrase>
signing.secretKeyRingFile=/Users/<you>/.gnupg/secring.gpg
```

Alternatively, use environment variables for CI:

```
ORG_GRADLE_PROJECT_mavenCentralUsername
ORG_GRADLE_PROJECT_mavenCentralPassword
ORG_GRADLE_PROJECT_signingInMemoryKey       # base64-encoded private key
ORG_GRADLE_PROJECT_signingInMemoryKeyId     # last 8 chars
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
```

## 4. Publish

### Local verification

Test the build locally first:

```bash
./gradlew publishToMavenLocal
```

Verify the artifact exists:

```bash
ls ~/.m2/repository/io/pandu/comet/comet/0.1.0/
```

Check the POM contains correct license, developer, and scm fields:

```bash
cat ~/.m2/repository/io/pandu/comet/comet/0.1.0/comet-0.1.0.pom
```

### Publish to Maven Central

```bash
./gradlew publishAllPublicationsToMavenCentralRepository
```

This uploads, closes, and releases the staging repository automatically (via the vanniktech maven-publish plugin).

## 5. klibs.io

No action needed. [klibs.io](https://klibs.io) automatically indexes KMP libraries from Maven Central within 24 hours once the POM contains a valid GitHub URL.

## Artifact Coordinates

| Artifact | Coordinates |
|----------|------------|
| Core library | `io.github.pandubaraja:comet:0.1.0` |
| Visualizer (future) | `io.github.pandubaraja:comet-visualizer:0.1.0` |
