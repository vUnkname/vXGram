# vXGram

A Telegram Android client based on [NagramXF](https://github.com/Keeperorowner/NagramXF) with integrated Xray and Aether proxy support.

Proxy cores:

- [patterniha/Xray-core](https://github.com/patterniha/Xray-core) — Xray engine (Android builds from [vUnkname/Xray-core](https://github.com/vUnkname/Xray-core))
- [CluvexStudio/Aether](https://github.com/CluvexStudio/Aether) — Aether engine (downloaded at runtime from releases)

Includes most features from exteraGram and AyuGram.

## Sponsor

* [爱发电](https://ifdian.net/a/nagramxf)
* [Ko-fi](https://ko-fi.com/nagramxf)

## Download

* [GitHub Releases](https://github.com/vUnkname/vXGram/releases)
* [Telegram Channel](https://t.me/NagramXF)
* [Telegram Beta Channel](https://t.me/NagramXFBetaAPKs)

## Build

### GitHub Actions (recommended)

You do **not** need a local JDK or Android SDK for release builds. CI downloads [vUnkname/Xray-core](https://github.com/vUnkname/Xray-core) during `prepareXrayCore` and produces APKs named `vXGram-<version>-<abi>.apk`.

| Workflow | Trigger | Output |
|----------|---------|--------|
| [vXGram build](.github/workflows/vxgram-build.yml) | **Actions → vXGram build → Run workflow** (any branch) | normal staging APKs (all ABIs + universal) |
| [Staging build](.github/workflows/staging.yml) | push to `dev` (create branch when needed) | normal + plugin staging APKs |
| [Canary build](.github/workflows/canary.yml) | push to `canary` (create branch when needed) | normal staging APKs |

Gradle task used by CI:

```bash
./gradlew TMessagesProj:assembleNormalStaging --stacktrace
```

**Repository secret (required):** `LOCAL_PROPERTIES` — Base64-encoded contents of your `local.properties` (Telegram `TELEGRAM_APP_ID` / `TELEGRAM_APP_HASH`, and keystore `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS`). The workflow passes it to Gradle the same way as a local build.

**Optional:** set `XRAY_VERSION` in [gradle.properties](gradle.properties) or in the workflow env to pin the Xray-core release (default `v26.10.3`).

**Firebase / FCM:** [TMessagesProj/google-services.json](TMessagesProj/google-services.json) uses `package_name` `org.telegram.messenger.vxgram`. CI can build with this file as-is. For push notifications in production, register the same application id in the [Firebase console](https://console.firebase.google.com/) and replace `google-services.json` with your project file.

### Local compilation (optional)

1. Clone the repository with its submodules:

    ```bash
    git clone --recursive --shallow-submodules https://github.com/vUnkname/vXGram.git
    cd vXGram
    ```

    If you already cloned the repository without submodules, run:

    ```bash
    git submodule update --init --recursive --depth=1
    ```

2. Obtain API credentials (`TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH`) from [Telegram Developer Portal](https://my.telegram.org/auth). Create `local.properties` in the project root with:

   ```properties
   TELEGRAM_APP_ID=<your_telegram_app_id>
   TELEGRAM_APP_HASH=<your_telegram_app_hash>
   ```

3. For APK signing: Replace `release.keystore` with your keystore and add signing configuration to `local.properties`:

   ```properties
   KEYSTORE_PASS=<your_keystore_password>
   ALIAS_NAME=<your_alias_name>
   ALIAS_PASS=<your_alias_password>
   ```

4. For FCM support: Replace `TMessagesProj/google-services.json` with your Firebase project file (package `org.telegram.messenger.vxgram`), or use GitHub Actions only and update Firebase when you need FCM.

5. Replace project-specific metadata:

    - Set your Google Maps API key in the `com.google.android.maps.v2.API_KEY` meta-data entry in `TMessagesProj/src/main/AndroidManifest.xml`.
    - Set `BaseRemoteHelper.CHANNEL_METADATA_ID` in `TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/remote/BaseRemoteHelper.java` to your metadata channel's numeric ID, without the `-100` prefix.

6. Build from the project root (JDK 21, Android SDK 36, NDK 27.2.12479018):

    ```bash
    chmod +x gradlew
    ./gradlew TMessagesProj:assembleNormalStaging --stacktrace
    ```

   Or open the project in Android Studio.

## Notice

This project reverse-engineered and uses some code from closed-source projects.

For the functionality obtained through reverse engineering in this section, I listed the original developers as the contributors.

## Acknowledgments

- [Keeperorowner/NagramXF](https://github.com/Keeperorowner/NagramXF) — base fork
- [CluvexStudio/Aether](https://github.com/CluvexStudio/Aether) — Aether proxy core
- [patterniha/Xray-core](https://github.com/patterniha/Xray-core) — Xray proxy core (Android builds: [vUnkname/Xray-core](https://github.com/vUnkname/Xray-core))
- [NagramX](https://github.com/risin42/NagramX)
- [AyuGram](https://github.com/AyuGram/AyuGram4A)
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram)
- [exteraGram](https://github.com/exteraSquad/exteraGram)
- [OctoGram](https://github.com/OctoGramApp/OctoGram)
