# Fluxa

Fluxa is an Android wallpaper app built with Kotlin and Jetpack Compose. It combines curated wallpaper feeds, search, local favorites, collections, offline caching, slideshow settings, and optional AI-assisted recommendations.

## Features

- Browse wallpapers from Unsplash, Pexels, Pixabay, Pinterest scraping, and built-in fallback sources.
- Search wallpapers with history and autocomplete-style suggestions.
- Explore category-based feeds such as Nature, Minimal, Abstract, Cyberpunk, Space, and Textures.
- Save wallpapers locally, pin favorites, and organize saved items into collections.
- Set wallpapers directly from the app.
- Work offline from cached wallpapers and fallback images.
- Configure source toggles, Wi-Fi-only loading, cache size, and slideshow behavior.
- Track anonymous preference signals with Firebase and use NVIDIA image analysis when configured.

## Tech Stack

- Kotlin
- Jetpack Compose and Material 3
- Gradle Kotlin DSL
- Room
- WorkManager
- Retrofit, OkHttp, Moshi, Jsoup, Coil
- Firebase Auth, Firestore, and Analytics
- Robolectric and Roborazzi for tests and screenshot coverage

## Requirements

- Android Studio
- JDK 11 or newer
- Android SDK with API 36 installed
- Firebase configuration in `app/google-services.json`
- API keys for the remote sources you want to enable

## Configuration

Create a local `.env` file from the example:

```bash
cp .env.example .env
```

Then fill in any keys you want to use:

```properties
UNSPLASH_ACCESS_KEY=...
PEXELS_ACCESS_KEY=...
PIXABAY_ACCESS_KEY=...
NVIDIA_API_KEY=...
```

The app can still run with fallback content when remote API keys are missing, but online feeds and AI analysis require valid keys.

For release signing, set these environment variables or provide `my-upload-key.jks` at the project root:

```bash
KEYSTORE_PATH=/path/to/upload-key.jks
STORE_PASSWORD=...
KEY_PASSWORD=...
```

## Run

Open the project in Android Studio and run the `app` configuration on an emulator or physical device.

From the command line:

```bash
./gradlew assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## Test

Run local unit tests:

```bash
./gradlew test
```

Run connected Android tests:

```bash
./gradlew connectedAndroidTest
```

Run a release build:

```bash
./gradlew assembleRelease
```

## Project Structure

```text
app/src/main/java/com/fluxawallpapers/app/
  data/             Data models, Room database, network APIs, repository, Firebase integration
  di/               App-level dependency wiring
  ui/               Compose theme, screens, shared components, and view models
  util/             Logging, hashing, and Pinterest scraping helpers
  worker/           Background heartbeat and slideshow workers
```

Supporting files:

- `firestore.rules` and `firestore.indexes.json`: Firebase Firestore rules and indexes.
- `.env.example`: Local API key template.
- `gradle/libs.versions.toml`: Central dependency and plugin versions.

## Repository Hygiene

Generated build outputs, local secrets, signing keys, Gradle caches, APK/AAB files, and local tool scratch folders are ignored by Git. Keep real credentials in `.env` or environment variables, not in committed files.
