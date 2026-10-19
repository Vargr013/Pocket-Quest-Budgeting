# Pocket Quest Budgeting

Android budgeting app (Jetpack Compose). Track expenses by category, attach optional receipt photos, review history for a date range, and keep data on-device with Room (SQLite).

## Video Demonstration

[https://www.youtube.com/watch?v=8R5WFqYDNKo](https://www.youtube.com/watch?v=8R5WFqYDNKo)

## Requirements

- Android Studio (recent stable)
- JDK 21 for the build and Firebase emulator checks
- Android device or emulator: **API 24+** (`minSdk 24`, `targetSdk 36`)

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

If an older checkout still reports a missing file, pull the shared configuration change. The file belongs at `app/google-services.json`, for example `D:\GitHub\Pocket-Quest-Budgeting\app\google-services.json`. If the client needs to be refreshed, download the matching Android file from **Firebase Console → Project settings → Your apps → Android**.

Firebase client configuration contains public project/app identifiers. Access to app data is enforced by Firebase Authentication and Security Rules. This file is different from an administrator/service-account key, which must stay private. See Firebase's [configuration documentation](https://firebase.google.com/docs/projects/learn-more#config-files-objects) and [API-key guidance](https://firebase.google.com/docs/projects/api-keys).

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

After cloud login, existing unlinked local accounts require an explicit linking choice and their old local password. Linking preserves the original local ID, records and receipts. Passwordless legacy accounts keep their data but require a separate recovery decision. You can instead choose a separate empty local account; matching names never merge data. See the [identity and linking contract](docs/FIREBASE_INTEGRATION.md).

First authentication/linking needs internet. Remember Me permits restoration of an already authenticated, mapped account; only a network failure allows its saved financial data to open offline. Logout closes that account's navigation/drafts and cancels account-specific work. New accounts start empty; server-aware defaults belong to the later category integration.

## What you can do

- Log in / register through Firebase, with a unique UID mapping to your local Room account
- Create and manage **categories**
- Add an **expense** (date, start/end time, description, category, amount)
- Optionally add a **receipt photo** (gallery or camera, copied into app storage)
- Open **History**, filter by period (all / week / month / custom), and view a saved photo
- Use **Dashboard** for an overview; **☰** opens extra screens (category spend, daily spend, achievements, saving goals, and more)

Data lives in the local Room database `pocket-quest.db`. Photos are files under the app’s private storage; expenses store the file path.

Financial record sync and cloud receipt transfer are not implemented yet. Linking marks existing data for the later import; it does not upload it. Android account/database backup is excluded so a copied session is not treated as a first login on another device.

To wipe local data while testing: device **Settings → Apps → Pocket Quest Budgeting → Storage → Clear data**, or uninstall and run again.

## Project layout

| Path | Role |
|---|---|
| `app/src/main/java/com/example/pocketquestbudgeting/ui/` | Screens and navigation (`PocketQuestNavigation.kt`) |
| `app/src/main/java/com/example/pocketquestbudgeting/data/` | Room entities, DAOs, database helper |
| `app/src/main/res/` | Strings, themes, drawables |

Main activity: `MainActivity.kt` → `PocketQuestNavigation`.

## Stack

Kotlin, Jetpack Compose, Navigation Compose, Room, Coil, Material 3.
