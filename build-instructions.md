# Build instructions

## Debug

With Gradle installed, run:

```sh
gradle assembleDebug
```

The debug APK will be written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release

Set the same Soneme signing variables used by the other Soneme Android projects:

```sh
export SONEME_KEYSTORE="$HOME/path to soneme-release.jks"
export SONEME_STORE_PASSWORD='keystore password'
export SONEME_KEY_PASSWORD='key password'
```

Then run:

```sh
gradle assembleRelease
```

The release APK will be written to:

```text
app/build/outputs/apk/release/app-release.apk
```

The signing alias is `soneme`. Keep using the same keystore for future updates.
