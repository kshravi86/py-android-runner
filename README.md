# Python Runner Android App

This Android sample ships with an embedded Chaquopy interpreter so you can write and execute Python snippets completely offline. The screen provides:

- A monospace editor for typing Python
- `Run` to execute the current buffer inside the bundled interpreter
- `Clear` to reset both the editor and output area
- A scrollable output panel which captures stdout, stderr, and tracebacks

## GitHub Actions build

Every push or pull request targeting `main` triggers `.github/workflows/android.yml`, which:

- Sets up Java 17 and the Gradle wrapper
- Caches Gradle dependencies
- Builds the debug APK via `./gradlew assembleDebug`
- Publishes the APK as the `app-debug-apk` artifact

Download the generated APK from the workflow run summary inside GitHub.
