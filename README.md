# Pocket Quest Budgeting

Android budgeting app built with Jetpack Compose. Track expenses and category limits, attach optional receipt photos, review spending by period, and set monthly spending goals. Room keeps account data on the device; Firebase Authentication and Firestore support accounts and financial record sync.

## Video Demonstration

[https://www.youtube.com/watch?v=8R5WFqYDNKo](https://www.youtube.com/watch?v=8R5WFqYDNKo)

This is the existing demonstration. A final phone recording showing the current online/offline flows and completed extra features is still required.

## Requirements

- Android Studio supporting the checked-in Android Gradle plugin 9.3.2 and Gradle 9.5.0 wrapper
- JDK 21 for the build and Firebase emulator checks
- Android SDK Platform 37 (SDK package `platforms;android-37.0`) and Android SDK Platform-Tools
- Android device or emulator: **API 24+** (`minSdk 24`, `targetSdk 36`)
- Internet for the first dependency download, registration/login and cloud sync

Use the included wrapper; a separate Gradle installation is unnecessary. Set your own SDK location in Android Studio, `ANDROID_HOME`, or ignored `local.properties`. Do not copy another computer's SDK path. Node.js, Firebase CLI and administrator credentials are not needed to build or use the normal app.

## Run the app

Complete the [Firebase setup](#firebase-setup) below before building. The app uses Firebase Authentication for username/password accounts and Room for local data.

1. Open this folder in Android Studio (**File → Open**).
2. Wait for Gradle sync to finish.
3. Select an emulator or a USB-connected phone with debugging enabled.
4. Click **Run**.

First launch: **Register** a username and password; successful registration signs you in. Returning users choose **Login**. Usernames are case-sensitive and immutable (1–32 UTF-8 bytes after trimming), and these username accounts have no email password-reset option.

## Firebase setup

The repository includes `app/google-services.json` for the team's `pocket-quest-c7127` Android app (`com.example.pocketquestbudgeting`). A fresh checkout can build with this shared client without downloading or copying a separate file.

### Team app with normal Firebase login

Build from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
```

If an older checkout still reports a missing file, pull the shared configuration change. The file belongs at `app/google-services.json`. Only refresh it from the matching Android app in Firebase Console if the shared configuration actually needs replacing.

Firebase client configuration contains public project/app identifiers. Access to app data is enforced by Firebase Authentication and Security Rules. This file is different from an administrator/service-account key, which must stay private. See Firebase's [configuration documentation](https://firebase.google.com/docs/projects/learn-more#config-files-objects) and [API-key guidance](https://firebase.google.com/docs/projects/api-keys).

The shared Android key is restricted to seven Firebase-related APIs: Firebase Management, Cloud Logging, Identity Toolkit, Token Service, Cloud Firestore, Cloud Storage for Firebase and Firebase Installations. I checked native Android login, forced token refresh and savings/achievement sync against the live project after applying this allowlist on 19 October 2026. Storage transfer was not part of that check.

For submission, Android package/signing-certificate restrictions are intentionally left off the shared key so markers can build with their own debug certificates. Markers do not need a separate Firebase key or to register their PC's signing fingerprint. They still need the Android build requirements above and internet access for initial registration/login and cloud sync. The API allowlist, Authentication and ownership rules remain in place.

GitHub secret scanning can flag this intentionally distributed client key. Review its current API restrictions and usage before resolving an alert; keeping the file in Git does not replace Authentication, Security Rules or applicable App Check protection. A real administrator credential or a key used for non-Firebase services needs a separate review.

An earlier `app/src/debug/google-services.json` demo file can override the shared client. Remove only that known demo override when returning to the team project. Variant overrides remain ignored by Git.

### Build-only or local emulator work

In a workspace intentionally configured without the shared client, the synthetic debug configuration can still be prepared explicitly. This uses Gradle and does not require Node.js:

```powershell
.\gradlew.bat :app:prepareFirebaseDemo
.\gradlew.bat :app:assembleDebug
```

The task creates ignored `app/src/debug/google-services.json` for `demo-pocket-quest`. It refuses to replace or override the included shared client or another selected configuration. It does not enable normal cloud login, route the app's login screen to local emulators, or configure release builds. `npm run firebase:demo` is the equivalent Node-based preparation command for configuration-free workspaces.

CI builds use the shared Android client. Emulator test scripts explicitly select the separate `demo-pocket-quest` project; they do not require replacing the shared file or connecting to the live backend.

Firebase's [Android setup guide](https://firebase.google.com/docs/android/setup) explains where to download the client file and why it belongs in the app module. Build checks with the synthetic client are separate from a real Firebase login or live-service test.

## Account behaviour

After cloud login, existing unlinked local accounts require an explicit linking choice and their old local password. Linking preserves the original local ID, records and receipts. Passwordless legacy accounts keep their data but require a separate recovery decision. You can instead choose a separate empty local account; matching names never merge data.

Firebase Auth identifies the cloud account by UID. The username/password screens use a case-sensitive, immutable username alias for Firebase's password sign-in; a username match alone never establishes ownership of local data. Each installation maps the UID to its own numeric Room user ID.

First authentication/linking needs internet. Remember Me permits restoration of an already authenticated, mapped account; only a network failure allows its saved financial data to open offline. Logout closes that account's navigation/drafts and cancels account-specific work. Category defaults are checked against both local and server state, so an established empty account is not repeatedly reseeded.

## What you can do

- Log in / register through Firebase, with a unique UID mapping to your local Room account
- Create and manage **categories**
- Add an **expense** (date, start/end time, description, category, amount)
- Optionally add a **receipt photo** (gallery or camera, copied into app storage)
- Open **History**, filter by period (all / week / month / custom), and view a saved photo
- Set overall monthly minimum/maximum spending goals and individual category limits
- Review daily/category spending and monthly category progress from saved records
- Use **Dashboard** for an overview; **☰** opens extra screens (category spend, daily spend, achievements, saving goals, and more)

Data lives in the local Room database `pocket-quest.db`. Photos are files under the app’s private storage; expenses store the file path.

Categories, expenses and monthly goals have Room/Firestore sync. Savings goals, immutable contributions and achievement evidence also have persistence and sync adapters. Add, Update and Remove Saving Goal use the existing savings adapters. Opening amounts and additions are retained events; the displayed balance is derived from that history.

Achievements calculate badges from saved expenses, budgets, category limits and activity days. Earned badges use the existing award storage and sync path. What It's Worth compares unused budgets with estimated catalogue prices or custom items; its "Total saved" is the unused budget from completed months with logged expenses, rather than savings-goal contributions. Activity days and custom items also sync through the account. Comparison preferences stay on the device. Historical category results use the current category limits.

Room version 13 adds stable cloud IDs and retained deletion markers to custom worth items. Forward migrations cover main's versions 10 to 12 and the standalone gamification version 10, retaining its original badges, activity days and custom items. Existing financial, receipt, savings and award tables remain intact.

Android account/database backup is excluded so a copied session is not treated as a first login on another device. Keep existing data when checking migrations or offline recovery; do not clear app storage to hide a database mismatch.

## Data and sync behaviour

Screens read account-scoped Room data. Synced writes save the local change and its durable outbox snapshot together. WorkManager retries with network access and restores pending work after restart. **Refresh**, **Retry failed** and **Review sync** expose recovery from the app. A successful local save does not mean the server has acknowledged it.

Cloud records live under `users/{uid}`:

| Path | Purpose |
|---|---|
| `categories` | Names, category limits and retained archive state |
| `expenses` | Expense values and stable category/photo references |
| `monthlyGoals` | One identity per calendar month with minimum/maximum limits |
| `savingsGoals` | Goal details and retained deletion markers |
| `savingsContributions` | Immutable opening/addition/full-reversal events |
| `earnedAchievements` | Supplied award evidence, criteria version and earned time/date |
| `activityDays` | One immutable recorded local-calendar day per account/date |
| `customWorthItems` | Named comparison values, stable IDs and retained removals |
| `mutations` | Immutable receipts used for safe transport retries |
| `savingsBalances`, `savingsReversals` | Internal paired-write guards |
| `receiptObjects` | Receipt reference lifecycle metadata |

Numeric Room IDs and private photo paths stay installation-local. Cloud references use stable IDs; amounts use whole-cent `Long` values, persisted dates use `YYYY-MM-DD`, and expense times use `HH:mm`. Contribution business IDs are retained across retries and are distinct from transport mutation IDs.

Revision conflicts keep the local draft for explicit review. Accepting the reviewed server record applies it to Room; an eligible mutable record can instead retain the reviewed local edit. Immutable contributions/awards cannot use local overwrite. A server deletion cannot be restored through conflict review. Archived categories and deleted goals retain history. Failed or conflicting savings history can wait for review rather than silently mix balances.

Legacy import discovers valid local rows repeatedly without duplicating accepted writes. Invalid rows stay local for review. Linking does not merge by username or remove receipt files.

For feature integration, use the existing adapters with a captured `SyncAccount`:

| Adapter | Mutations |
|---|---|
| `CategorySyncs.get(context)` | `create`, `rename`, `setBudgets`, `archive` |
| `ExpenseSyncs.get(context)` | `save`, `delete` |
| `MonthlyGoalSyncs.get(context)` | `saveForMonth` |
| `SavingsSyncs.get(context)` | `createGoal`, `editGoal`, `updateGoalWithContribution`, `removeGoal`, `recordContribution`, `recordEarned` |
| `GamificationSyncs.get(context)` | `markActiveDay`, `saveItem`, `removeItem` |

Keep Room reads scoped to the captured local user. Savings edit/remove calls take the reviewed goal; stale drafts must be reopened. Saved balances are derived from contributions, not set as a separate field. A positive opening uses `opening:<goal UUID>`; zero creates no event. A full reversal can be recorded once and keeps the original history.

Achievement evidence is owner-supplied. `AchievementLoader` evaluates the app's earning criteria and records each newly earned badge once, using a stable `gamification:v1:<badge ID>` award key. Existing awards and their supplied evidence are retained. The deployed rules validate ownership and record shape; they do not independently verify the earning criteria.

Activity dates use the date recorded on the reviewing/logging device. Downloads and background sync do not mark today active. Two devices publishing the same day converge on one fact. Custom comparison edits use revision review; removals retain tombstones and cannot be restored by a stale edit. Independent same-name items keep separate identities.

Savings forms accept a strict optional `dd/MM/yyyy` target date and exact whole-cent amounts. Opening amounts/additions use today's captured business date, independently of the target date. Update commits its metadata and optional addition together locally; cloud publication and conflicts remain separate. Repeated submission retains the original business identity.

If two devices independently award the same badge with different earned evidence, the local draft stays available for reviewed server acceptance. Accepted cloud awards are immutable. The open screens refresh from local database changes, without overwriting a dirty savings/custom-item form.

## Receipt and service status

The new activity/custom-item paths need this revision's Firestore rules deployed before live sync can succeed. The implementation and local tests do not deploy those rules. If an earlier attempt was denied, use Retry failed after the release so the retained operations can publish.

Local camera/gallery receipts remain supported. Receipt jobs and cache handling support KEEP/REPLACE/REMOVE, retries and retained-object lifecycle checks. The existing Firebase Storage emulator tests exercise transfer behaviour.

The 19 October 2026 release verified Firestore rules for the existing Native database in `africa-south1`. The later key review verified native Auth/Firestore adapter checkpoints. These dated checks do not establish a final phone demonstration or two independent installations.

Billing was disabled in that service snapshot. Storage rules and live receipt-photo transfer have not been deployed/verified. Firestore metadata sync does not prove that another device can download a photo. Firebase currently requires the Blaze plan for [Cloud Storage](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024); any billing/bucket/Storage release needs a separate decision. Local/emulator receipt tests do not enable that service.

## Build and verification

From PowerShell in the repository:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --console=plain
```

For Linux/macOS, first run `chmod +x gradlew`, then use `./gradlew` with the same tasks. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. It is signed with the builder's debug certificate; no private release keystore is required for this build.

Local Firebase test development additionally needs Node.js 22.x, JDK 21 and `npm ci`. The locked Firebase CLI is used by the npm scripts. All emulator suites select `demo-pocket-quest`, with host Auth `127.0.0.1:9099`, Firestore `127.0.0.1:8080` and Storage `127.0.0.1:9199` where required. Named Android test clients use the emulator host bridge; tests do not replace the shared client.

Android instrumentation is restricted to the dedicated `PocketQuest_Account_Tests_API35` on `emulator-5554`. Before any install/test command, verify that it is the sole connected device and has finished booting:

```powershell
$env:ANDROID_HOME = '<your Android SDK folder>'
$env:ANDROID_SERIAL = 'emulator-5554'
& "$env:ANDROID_HOME/platform-tools/adb.exe" devices
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 emu avd name
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell getprop sys.boot_completed
```

The returned name must match exactly and boot completion must be `1`. Keep personal/presentation AVDs disconnected. Run Gradle and these suites sequentially:

| Command | Coverage |
|---|---|
| `npm run test:database` | Android Room, additive migrations and local fixture/coordinator regression; no Firebase emulator needed |
| `npm run test:savings` | Savings/award integration using root Firestore rules |
| `npm run test:gamification` | Strict two-client activity/custom-item sync, migration and custom/savings screen flows |
| `npm run test:rules` | Direct root Firestore/Storage security rules |
| `npm run test:receipts` | Receipt jobs/cache and strict emulator transfer |
| `npm run test:expenses` | Expense sync/import with root Firestore rules |
| `npm run test:goals` | Monthly-goal sync/import with root Firestore rules |
| `npm run test:categories` | Category/default/archive sync with root Firestore rules |
| `npm run test:sync` | Broad account/transport regression using explicitly permissive Firestore fixtures |
| `npm run test:accounts` | Focused Auth-emulator entry point when an independent account run is needed |

On Windows, use `npm.cmd` if PowerShell blocks the npm script shim. Preserve installed fixture data when the runner requests it. Copy reports after each run because later runs can replace them. The suites overlap; do not add their counts as unique tests. Permissive transport fixtures are not production security-rule evidence.

GitHub Actions builds debug and instrumentation APKs, runs JVM tests/lint, and checks the root rules. It does not execute Android instrumentation. Android setup explicitly selects `platform-tools` to avoid the obsolete `tools` package in the action's default list ([Android Actions, 2026](https://github.com/android-actions/setup-android/blob/v3/action.yml)). A workflow edit or local pass does not establish hosted success; the relevant completed run must be checked separately.

### Verified on 20 October 2026

Local checks used `a95c6b5` with the pending workflow/README changes. No application, database schema or production-rule source was changed by this patch.

| Check | Result |
|---|---|
| Debug and instrumentation APK assembly | Passed |
| JVM tests | 18 passed; no failures/errors/skips |
| Lint | 0 errors, 28 warnings, 14 hints |
| Room/database Android regression | 100 passed |
| Strict savings / receipt / expense / monthly-goal / category suites | 29 / 27 / 18 / 25 / 13 passed |
| Direct production-rule emulator suite | 65 passed |
| Permissive account/transport regression | 128 passed; not production security-rule proof |
| Isolated tracked-source debug build | Passed with a newly generated debug certificate and the shared client |
| Current live configuration, read-only | Firestore source matched local rules; billing disabled; seven-API allowlist and submission certificate policy retained |
| Hosted execution of this patch | Pending; latest existing run failed during SDK setup before build/tests |

Android suites used the guarded API 35 AVD and had no failures/errors/skips. Counts overlap. The rules run retained the known `storage.rules:20:16` null-value diagnostic when denying an upload without its required guard document; that denial passed. Storage rules were not changed or deployed.

The isolated build reused this machine's SDK/dependency cache and did not run on a marker's PC or perform a new live login. Live checks in this patch read service configuration only; no live fixture accounts or financial records were created.

### Marker checklist

1. Clone the source repository, open it in Android Studio and use JDK 21.
2. Install the required SDK packages and set your own SDK location.
3. Keep the included shared client; check for an old ignored variant override if normal login selects the wrong project.
4. Build the debug APK with the wrapper and run on an API 24+ device.
5. Register a new synthetic account while online, save a category/expense/monthly goal, and use Refresh to check cloud sync.
6. Check the same account from a separate installation. A local export build is not evidence that this manual check has happened.

Final acceptance still needs a relevant hosted run, independent-installation/phone evidence, a narrated final phone video showing online storage, and both agreed own features working through their screens. The original design documents and lecturer feedback must be reconciled before claiming final submission readiness.

## Project layout

| Path | Role |
|---|---|
| `app/src/main/java/com/example/pocketquestbudgeting/ui/` | Screens and navigation (`PocketQuestNavigation.kt`) |
| `app/src/main/java/com/example/pocketquestbudgeting/data/` | Room entities, DAOs, database helper |
| `app/src/main/res/` | Strings, themes, drawables |

Main activity: `MainActivity.kt` → `PocketQuestNavigation`.

## Stack

Kotlin, Jetpack Compose, Navigation Compose, Room, Coil, Material 3.
