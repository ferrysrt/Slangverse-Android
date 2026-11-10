# Development directly from ChatGPT (no Codex needed)

1. ChatGPT can write and edit the Kotlin, Jetpack Compose, Node.js, tests, and documentation, then provide updated ZIP source files in chat.
2. ChatGPT can execute local Node.js backend tests in its working environment, but cannot compile Android without a usable Android SDK/Gradle toolchain.
3. If you want direct repository updates, create a new private `Slangverse-Android` repository and grant the connected GitHub app write access to it. **Never overwrite `slangfeed`.**
4. If your laptop cannot build Android, use the bundled `.github/workflows/android-debug.yml` through GitHub Actions after creating/pushing the new repository. It installs Android SDK + Gradle in the cloud and uploads a debug APK **only if compilation succeeds**. This workflow has not been executed yet.
5. Unzip this package and follow `README.md`. Open the `android/` directory in Android Studio to sync/build; use the mock backend for early testing.
6. For iterative improvements, provide Android Studio's first Gradle error or log. ChatGPT can edit the exact failing files and return a revised archive or, when the GitHub repository exists and has appropriate permissions, make changes via the GitHub integration.
7. When a real API key is available, configure it **privately** on the backend, never paste credentials in source files, screenshots, or chat messages.
