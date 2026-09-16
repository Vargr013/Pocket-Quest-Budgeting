# Pocket Quest Budgeting

Android budgeting app (Jetpack Compose). Track expenses by category, attach optional receipt photos, review history for a date range, and keep data on-device with Room (SQLite).

## Video Demonstration

[https://www.youtube.com/watch?v=8R5WFqYDNKo](https://www.youtube.com/watch?v=8R5WFqYDNKo)

## Requirements

- Android Studio (recent stable)
- JDK 17
- Android device or emulator: **API 24+** (`minSdk 24`, `targetSdk 36`)

## Run the app

1. Open this folder in Android Studio (**File → Open**).
2. Wait for Gradle sync to finish.
3. Select an emulator or a USB-connected phone with debugging enabled.
4. Click **Run**.

First launch: **Register** a username and password, then **Login**.

## What you can do

- Log in / register (account stored locally)
- Create and manage **categories**
- Add an **expense** (date, start/end time, description, category, amount)
- Optionally add a **receipt photo** (gallery or camera, copied into app storage)
- Open **History**, filter by period (all / week / month / custom), and view a saved photo
- Use **Dashboard** for an overview; **☰** opens extra screens (category spend, daily spend, achievements, saving goals, and more)

Data lives in the local Room database `pocket-quest.db`. Photos are files under the app’s private storage; expenses store the file path.

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
