# YmnTube Android

This repository contains the YmnTube app source and build configuration. The app is based on YmnTube and is licensed under GPL-3.0-or-later; see [LICENSE](LICENSE).

## Build an APK locally

Install JDK 21 and the Android SDK, then run the Gradle wrapper from the repository root:

```shell
./gradlew assembleDebug
```

On Windows, run `.\gradlew.bat assembleDebug`. Gradle downloads the required Android dependencies and SDK components. The APK is written to `app/build/outputs/apk/debug/`.

## Build with GitHub Actions

The **Build Android APK** workflow runs on pushes to `main` and can also be started manually from the Actions tab. Download the `YmnTube-debug-apk` artifact from a successful run.

The workflow builds a debug APK, which Android's build tools sign with the automatically generated debug key. It is suitable for testing and direct installation, not for publishing as a production release. A production-signed release requires a private release keystore and its passwords to be configured as GitHub Actions secrets, plus release signing configuration in Gradle.
