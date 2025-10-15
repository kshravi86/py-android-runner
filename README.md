# Simple Editor Android App

This repository hosts a minimal Android note editor built with Kotlin. The UI provides a basic text area with *Save* and *Clear* actions; text is persisted locally using shared preferences.

## GitHub Actions build

Every push or pull request targeting `main` triggers `.github/workflows/android.yml`, which:

- Sets up Java 17 and Gradle wrapper
- Caches Gradle dependencies
- Builds the debug APK via `./gradlew assembleDebug`
- Publishes the APK as the `app-debug-apk` artifact

You can download the generated APK from the workflow run summary inside GitHub.
