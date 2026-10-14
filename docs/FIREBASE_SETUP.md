# Firebase setup and rules checks

Slice 2, 14 October 2026. Firestore was confirmed for emulator/CI work. No live project was supplied, configured or deployed. The app still uses local login and user-scoped Room v4; these SDKs do not synchronise Room or implement slice 3 authentication.

The integration contract and shared reference list are in `docs/FIREBASE_INTEGRATION.md`. At the start of this slice that local draft was untracked and hidden by `*.md`. The ignore exceptions now expose the contract and this setup document for review; neither has been staged. The external plan and handover stay outside this repository. Authentication alias/recovery/linking decisions D2/D3 and the later data policies remain open.

## Android debug build without a real project

Use JDK 21, Node 22.14.0/npm 10, Android SDK platform `android-37.0`, and this checkout's Gradle wrapper. The current checkout uses AGP 9.3.2, Gradle 9.5.0, Kotlin 2.2.10, KSP 2.3.2, compile SDK 37, minimum SDK 24 and target SDK 36. Existing versions/dependencies were retained. SDK packages can be installed with `sdkmanager "platforms;android-37.0"` (Google, 2026ae).

From the repository root, PowerShell:

```powershell
npm.cmd ci
npm.cmd run firebase:demo
.\gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
npm.cmd run test:rules
```

On Linux/CI use `npm` instead of `npm.cmd`, then `chmod +x gradlew` and `./gradlew` instead of `.\gradlew.bat`. Set `JAVA_HOME` to JDK 21 and configure Android Studio's SDK or `ANDROID_HOME`. `local.properties` remains machine-local.

`firebase:demo` copies the tracked, deliberately fake `config/firebase/google-services.demo.json` to ignored `app/src/debug/google-services.json`. It refuses to overwrite a different debug configuration or mix with `app/google-services.json`. It is repeatable and needs no installed npm packages. The Google services plugin validates the package and generates debug resources. Firebase BoM 34.19.0 supplies Auth 24.2.0, Firestore 26.6.0 and Storage 22.0.2 main modules; no Firebase `-ktx`, Analytics or Android Admin SDK was added (Google, 2026m; Google, 2026af).

BoM 35.0.0 was tested first, but Auth 25.0.0 contains Kotlin 2.4 metadata that this checkout's Kotlin 2.2.10 compiler rejected. The selected 34.19.0 BoM is the last 34.x release listed before 35.0.0. Preserve this compatible pin rather than suppress metadata checks or upgrade unrelated Kotlin/Compose tooling (Google, 2026af).

The fake key/project/app ID only make a debug build reproducible. They cannot authenticate against a real backend. No release client configuration is supplied; release builds need a separately selected real configuration. Do not distribute the demo build as proof of online functionality.

`INTERNET` and `ACCESS_NETWORK_STATE` are normal manifest permissions, without runtime permission prompts. No broad cleartext exception was added (Google, 2026aa).

## Emulator rules checks

`npm ci` installs exact direct versions plus the committed transitive lockfile. The rules harness uses Firebase JS 12.10.0 and rules-unit-testing 5.0.1 on Node 22; the newer major harness requires Node 24. The separate test tooling does not change any Android dependency. Firebase CLI is pinned to 15.33.0.

`npm run test:rules` runs:

```text
firebase emulators:exec --project demo-pocket-quest --only firestore,storage "node --test --test-concurrency=1 firebase-tests/*.test.mjs"
```

The CLI starts Firestore on `127.0.0.1:8080` and Storage on `127.0.0.1:9199`, runs the tests, propagates their exit code and stops the processes. Java 21 supports the current emulator direction. First use downloads emulator binaries; allow network access for those downloads and npm/Gradle dependencies. No Firebase login, service account, production token, paid bucket or project provisioning is needed (Google, 2026ac; Google, 2026ad).

Tests refuse an unexpected project or non-loopback emulator environment. Synthetic Alice/Bob contexts and tiny PNG fixtures are cleared between cases; rules-disabled setup seeds only test fixtures. The Node harness sends direct client reads/writes to the actual emulators. A denial must carry a permission error, not just fail for a connection problem (Google, 2026o).

The Android app does **not** connect to these emulators or send any cloud records in this slice. A later debug-only integration must call the Auth/Firestore/Storage `useEmulator` APIs before first use; Android emulator host access uses `10.0.2.2`, unlike the desktop Node tests. Keep that routing out of release builds and disable Firestore persistence for deterministic emulator tests. Auth Emulator sign-in and UID-to-Room migration belong to slice 3 (Google, 2026ad).

## Initial access boundary

| Path/operation | Initial behaviour |
|---|---|
| Firestore `users/{uid}` get | Authenticated path owner only. |
| Setup create | Owner only; exactly `schemaVersion: 1` (integer), nonempty string `username`, `categoriesInitialized: false`. No submitted ownership/password fields. |
| Setup update | Owner only; same username, exact fields, flag still false. Idempotent initial setup is allowed. |
| User list/delete | Denied, including to owner. |
| Nested categories, expenses, monthly goals, mutations, savings, achievements, deeper/unmatched documents | All reads/writes denied, including to owner. Add their field/revision/reference/tombstone rules when their slices implement them. |
| Storage `users/{uid}/receipts/{expenseCloudId}/{receiptJobId}` get/create/delete | Path owner only; both IDs must be lowercase UUID-shaped strings. |
| Receipt create | No existing object at the path; nonempty, at most 10 MiB; declared `image/jpeg` or `image/png`; no application custom metadata or ownership fields. |
| Receipt replacement/metadata update | Denied. A replacement needs a new job path. Slice 8 must handle retry verification of an existing immutable upload. |
| Storage list or any other path | Denied to everyone. Fetch known object paths rather than enumerate the bucket. |

UID ownership comes from Auth and the path, never a payload field. Setup username is only a nonempty display field here; it is not an authentication mapping, uniqueness check, verified email, or approval of the pending alias policy. Slice 3 must extend validation after D2/D3 are agreed. The setup flag remains locked until atomic defaults/category initialisation is implemented; an existing true flag cannot be reset. Firestore parent permissions do not grant nested collection access (Google, 2026p; Google, 2026z).

Receipt metadata/path limits are an initial emulator safety boundary based on the draft contract, not approval of a paid storage service or final image policy. Storage rules cannot decode image bytes or prove the MIME declaration is truthful. Slice 8 must validate decoded JPEG/PNG bytes and matching MIME, require an existing own expense before upload, and check reference/pending-publication state before cleanup. This initial ruleset has no cross-service expense check because expense writes are still closed. Do not deploy it as a finished receipt integration. Delete checks only owner/path, so a legacy file with invalid upload metadata remains removable (Google, 2026y; Google, 2026ab).

The emulator strips the reserved `firebaseStorageDownloadTokens` key from submitted custom metadata before rule evaluation; the application metadata allowlist does not prove that reserved token creation is prevented. Use authenticated SDK reads and persist only object paths, as the contract requires; no public download URL handling is implemented here. Live download-token behaviour remains unverified.

## Real project configuration, pending

Before live integration, obtain the existing project ID, responsible contact/member access and Android registration for `com.example.pocketquestbudgeting`. Agree Firestore Standard database location, Email/Password provider setup and the username alias/linking/recovery policy. Obtain the real bucket name/location and explicit billing authorisation before any Storage provisioning. Cloud Storage requires Blaze; the emulator does not establish live bucket availability (Google, 2026t).

Download Android **client** `google-services.json` from the approved project. Remove the generated demo `app/src/debug/google-services.json` explicitly before selecting a real common file at `app/google-services.json`, or replace the debug file with a real debug client configuration. Variant-specific files take precedence, so check the generated resources to confirm the selected project. Keep release and demo configurations separate (Google, 2026m).

Firebase Android client configuration contains project/app identifiers and an API key, not an Admin credential; it may be tracked if the team approves the project identifiers and appropriate key restrictions. This slice keeps real client selection excluded via `**/google-services.json`, while tracking the synthetic source. Rules/Auth provide access control. Never place Admin SDK service-account JSON, private keys, passwords, refresh tokens or deployment credentials in Android or this repository (Google, 2026s).

The current CI generates its debug client file from the tracked synthetic source, so forks/PRs need no secrets. If future CI deliberately builds against an excluded real client file, an authorised maintainer can store that **client configuration only** in a GitHub Actions secret such as `FIREBASE_ANDROID_CLIENT_JSON`, materialise it to `app/google-services.json` in a protected job without logging it, and omit the demo-generation step. Fork PRs cannot receive that secret and should keep the synthetic job. No production credentials are needed for rules tests.

## CI and evidence

`.github/workflows/firebase-checks.yml` runs on push and pull request with read-only repository permissions. It installs JDK 21/Node 22.14.0, the current compile SDK, locked npm packages and synthetic debug configuration, then runs the same Android build/lint and emulator rules commands. It contains no deploy or Firebase-login step. A workflow file is not proof of a hosted run.

Local results and next-slice actions are recorded in the external `PART_A_SLICE_2_HANDOVER.md`. Build/lint, direct emulator tests, hosted CI and physical-device evidence are separate gates. There are still no Android app test sources; `:app:testDebugUnitTest` reporting `NO-SOURCE` is not a passing app suite. No physical-phone or cross-device cloud demonstration is claimed.

Local verification on 14 October 2026 passed `assembleDebug` and `lintDebug` with BoM 34.19.0 (0 lint errors, 22 warnings and 10 hints). The completed emulator suite passed all 22 tests with no failures or skips. Workflow YAML, configuration JSON, JavaScript syntax, citation labels and whitespace were checked locally. Hosted CI, live cloud configuration and manual device behaviour remain unverified.

On 14 October 2026, `npm audit --package-lock-only` reported 17 advisories in the development/test dependency tree (5 moderate, 12 high; none critical). This is not a clean dependency security audit. No forced fix, transitive override or suggested major downgrade was applied. These Node dependencies are separate from the Android APK; review the tooling advisories before expanding the CI job's access or using it with real services.
