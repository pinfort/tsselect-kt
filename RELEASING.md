# Releasing `me.pinfort:tsselect`

The library module (`tsselect-core`) is published to Maven Central through the
[Sonatype Central Portal](https://central.sonatype.com). `tsselect-cli` is an
application and is deliberately not published to Central — it ships instead as a
distribution archive attached to the GitHub release for the tag.

Everything Gradle-side is already wired up (`tsselect-core/build.gradle.kts` and
`.github/workflows/release.yml`). What remains is account setup, which is manual
and one-time.

## One-time setup

### 1. Central Portal account

Sign in at <https://central.sonatype.com> (GitHub login is fine).

### 2. Claim the `me.pinfort` namespace

Namespaces on Central must be proved. `me.pinfort` is a *domain* namespace, so it
is proved by DNS on **pinfort.me**:

1. Portal → **Namespaces** → **Add Namespace** → `me.pinfort`.
2. The portal shows a verification key, e.g. `abc123xyz`.
3. Add a TXT record on the apex of `pinfort.me` with that key as its value.
4. Back in the portal, press **Verify Namespace**. DNS propagation can take a
   while; the check is re-runnable.

Once verified the TXT record can be removed, and every artifact under
`me.pinfort` is yours to publish.

> If pinfort.me is not actually yours, the alternative is the `io.github.pinfort`
> namespace, verified by creating a public GitHub repo named after a code the
> portal gives you. That means changing `group` in `gradle.properties`; the
> Kotlin package names do not have to change.

### 3. Generate a user token

Portal → your account → **Generate User Token**. This yields a username/password
pair — *not* your login credentials. These become the `MAVEN_CENTRAL_USERNAME`
and `MAVEN_CENTRAL_PASSWORD` secrets.

### 4. Create a signing key

Central rejects unsigned artifacts. Generate a key, publish the public half so
Central can verify against it, and export the private half for CI:

```bash
gpg --full-generate-key            # RSA 4096, no expiry or a long one, use a passphrase

gpg --list-secret-keys --keyid-format=long     # note the fingerprint
FPR=<the fingerprint>

# Central verifies against a public keyserver — this step is not optional.
gpg --keyserver keyserver.ubuntu.com --send-keys "$FPR"

# The private key, armoured, for the CI secret. Keep this out of the repo.
gpg --armor --export-secret-keys "$FPR"
```

### 5. Add the GitHub secrets

The release workflow runs in the `maven-central` environment. Create it under
**Settings → Environments** (adding a required reviewer there gives you a manual
approval gate on every release), then add these four secrets to it:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | user token username from step 3 |
| `MAVEN_CENTRAL_PASSWORD` | user token password from step 3 |
| `SIGNING_IN_MEMORY_KEY` | the full `-----BEGIN PGP PRIVATE KEY BLOCK-----` output from step 4 |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | the passphrase on that key |

## Cutting a release

1. Set the version in `gradle.properties` to the release version and commit it.
   (The workflow passes `-Pversion` from the tag, so this is bookkeeping for
   local builds and for the next `-SNAPSHOT`-free state of the tree — but keeping
   the two in step avoids confusion.)
2. Tag and push:

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

3. The **Release** workflow runs the test suite once (`prepare`), then fans out:

   - `cli` builds `tsselect-<version>.zip` / `.tar.gz`, unpacks the zip and runs
     the launcher as a smoke test, then attaches both archives plus
     `tsselect-<version>-SHA256SUMS.txt` to the GitHub release for the tag,
     creating that release with generated notes if it does not exist yet. It uses
     the built-in `GITHUB_TOKEN` (`contents: write` on that job only) — no secrets.
   - `publish` uploads a signed bundle to the Central Portal. It stops at the
     `PUBLISHING`/`VALIDATED` state.

   The two are independent, so a Central failure — or a pending reviewer approval
   on the `maven-central` environment — does not hold up the download.
4. Go to Portal → **Deployments**, check the contents, and press **Publish**.
   The artifact appears on Central within ~15 minutes and on search shortly after.
5. Edit the release notes on GitHub if the generated ones need a human pass.

To skip step 4 and have a tag push go all the way to Central, change the workflow's
publish task from `publishToMavenCentral` to `publishAndReleaseToMavenCentral`.

**A published version is permanent.** Central does not allow replacing or deleting
a released version — a mistake costs you a version number.

A `workflow_dispatch` run does everything except touch a GitHub release (there is
no tag to attach to): the CLI archives are left as a workflow artifact on the run.

## Checking things locally first

The CLI archives, exactly as the `cli` job builds them:

```bash
./gradlew -Pversion=<version> :tsselect-cli:distZip :tsselect-cli:distTar
ls tsselect-cli/build/distributions/
```

The library's full artifact set into `~/.m2` — unsigned, since no key is configured:

```bash
./gradlew :tsselect-core:publishToMavenLocal
ls ~/.m2/repository/me/pinfort/tsselect/<version>/
```

Expect five artifacts: the jar, `-sources.jar`, `-javadoc.jar`, `.pom` and
`.module`. To rehearse the signing path too, set
`ORG_GRADLE_PROJECT_signingInMemoryKey` and
`ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` in the environment and run the
same command — each artifact should gain a `.asc` alongside it.
