# Pocket Quest Budgeting

Android budgeting app (Jetpack Compose). Track expenses by category, attach optional receipt photos, review history for a date range, and keep data on-device with Room (SQLite).

## Video Demonstration

[https://www.youtube.com/watch?v=8R5WFqYDNKo](https://www.youtube.com/watch?v=8R5WFqYDNKo)

## Requirements

- Android Studio (recent stable)
- JDK 21 for the build and Firebase emulator checks
- Android device or emulator: **API 24+** (`minSdk 24`, `targetSdk 36`)

## Run the app

Complete the [Firebase client configuration and test setup](docs/FIREBASE_SETUP.md) before Gradle sync. The app uses Firebase Authentication for username/password accounts and Room v5 for financial data. A synthetic client from `npm run firebase:demo` supports builds/CI only; normal app login needs a configured real project. Keep the selected real client local and do not mix it with a debug demo override.

1. Open this folder in Android Studio (**File → Open**).
2. Wait for Gradle sync to finish.
3. Select an emulator or a USB-connected phone with debugging enabled.
4. Click **Run**.

First launch: **Register** a username and password; successful registration signs you in. Returning users choose **Login**. Usernames are case-sensitive and immutable (1–32 UTF-8 bytes after trimming), and these username accounts have no email password-reset option.

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
