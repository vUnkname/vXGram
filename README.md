# vXGram

Telegram for Android with integrated Xray and Aether proxy support (**vXGram**).

Proxy cores:

- [patterniha/Xray-core](https://github.com/patterniha/Xray-core) — Xray engine (Android builds from [vUnkname/Xray-core](https://github.com/vUnkname/Xray-core))
- [CluvexStudio/Aether](https://github.com/CluvexStudio/Aether) — Aether engine (downloaded at runtime from releases)

Includes most features from exteraGram and AyuGram.

## Version

User-facing version: **`APP_VERSION_NAME-vX.VX_VERSION_NAME`** → currently **`12.10.1-vX.1.0.0`**.

| What | Where | Current value |
|------|--------|----------------|
| **Telegram base** | `APP_VERSION_NAME` / `APP_VERSION_CODE` in [gradle.properties](gradle.properties) | **12.10.1** / **7038** |
| **vXGram product** | `VX_VERSION_NAME` / `VX_VERSION_CODE` in [gradle.properties](gradle.properties) | **1.0.0** / **1** |
| **Android `versionName`** | [TMessagesProj/build.gradle](TMessagesProj/build.gradle) | `12.10.1-vX.1.0.0` |
| **Android `versionCode`** | `APP_VERSION_CODE * 100 + VX_VERSION_CODE` | **703801** |

Bump `VX_VERSION_NAME` / `VX_VERSION_CODE` for each vXGram release on the same Telegram base. After rebasing to a new Telegram release, update `APP_VERSION_*` and reset `VX_VERSION_CODE` if you start a new product line on that base.

## Download

* [GitHub Releases](https://github.com/vUnkname/vXGram/releases)
* [Telegram Channel](https://t.me/vXGramOffical)

## Build

### APK file names

Gradle renames staging outputs in [TMessagesProj/build.gradle](TMessagesProj/build.gradle):

| Flavor | Meaning | Example |
|--------|---------|---------|
| **normal** | Telegram + Xray/Aether, no Python plugin runtime | `vXGram-12.10.1-vX.1.0.0-arm64-v8a.apk` |
| **plugin** | Same + plugin support (Chaquopy) | `vXGram-12.10.1-vX.1.0.0-plugin-arm64-v8a.apk` |

**normal:** all ABIs (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) + **universal**.  
**plugin:** `armeabi-v7a` and `arm64-v8a` only.

### GitHub Actions (recommended)

You do **not** need a local JDK or Android SDK for release builds. CI downloads [vUnkname/Xray-core](https://github.com/vUnkname/Xray-core) during `prepareXrayCore`.

| Workflow | Trigger | Output |
|----------|---------|--------|
| [vXGram build](.github/workflows/vxgram-build.yml) | **Actions → vXGram build → Run workflow** | **normal + plugin** staging APKs |
| [Staging build](.github/workflows/staging.yml) | push to `dev` | normal + plugin staging APKs |
| [Canary build](.github/workflows/canary.yml) | push to `canary` | normal staging APKs only |

Gradle task used by **vXGram build** and **Staging build**:

```bash
./gradlew TMessagesProj:assembleStaging --stacktrace
```

**Repository secret (optional but recommended):** `LOCAL_PROPERTIES` — Base64-encoded `local.properties` with your `TELEGRAM_APP_ID` / `TELEGRAM_APP_HASH` and release keystore passwords. If unset, CI signs with the bundled dummy `release.keystore` and values from [gradle.properties](gradle.properties) (`RELEASE_*`).

**Optional:** set `XRAY_VERSION` in [gradle.properties](gradle.properties) or in the workflow env to pin the Xray-core release (default `v26.10.3`).

**Firebase / FCM:** [TMessagesProj/google-services.json](TMessagesProj/google-services.json) uses `package_name` `org.telegram.messenger.vxgram`.

### Local compilation (optional)

1. Clone with submodules:

    ```bash
    git clone --recursive --shallow-submodules https://github.com/vUnkname/vXGram.git
    cd vXGram
    ```

2. Create `local.properties` with `TELEGRAM_APP_ID`, `TELEGRAM_APP_HASH`, and signing keys (see [Telegram Developer Portal](https://my.telegram.org/auth)).

3. Full staging build (normal + plugin):

    ```bash
    chmod +x gradlew
    ./gradlew TMessagesProj:assembleStaging --stacktrace
    ```

   Outputs:

   - `TMessagesProj/build/outputs/apk/normal/staging/`
   - `TMessagesProj/build/outputs/apk/plugin/staging/`

   **normal only:**

    ```bash
    ./gradlew TMessagesProj:assembleNormalStaging --stacktrace
    ```

4. JDK 21, Android SDK 36, NDK 27.2.12479018 — or open in Android Studio.

## Notice

This project reverse-engineered and uses some code from closed-source projects.

For the functionality obtained through reverse engineering in this section, I listed the original developers as the contributors.

## Acknowledgments

- [Keeperorowner/NagramXF](https://github.com/Keeperorowner/NagramXF) — base fork
- [CluvexStudio/Aether](https://github.com/CluvexStudio/Aether) — Aether proxy core
- [patterniha/Xray-core](https://github.com/patterniha/Xray-core) — Xray proxy core (Android builds: [vUnkname/Xray-core](https://github.com/vUnkname/Xray-core))
