# Building the release APK

The release APK is the one to put on a phone: R8 shrinks and optimizes the code and removes
unused resources, so it is about 3.3 MB (the debug APK is about 27 MB) and runs faster.
Release builds talk to the backend in `secrets.properties` over HTTPS only.

All commands run in **Git Bash** from the project folder.

## 1. One time: create your signing key

Android only installs an update over an app signed with the **same key**. Make one key and keep it:
if it's lost, phones must uninstall the app (and its local data) to install a newer version.

```bash
"/c/Program Files/Java/jdk-19/bin/keytool.exe" -genkeypair -v -keystore reelcounter-release.jks -alias reelcounter -keyalg RSA -keysize 2048 -validity 10000
```

It asks for a password (choose your own) and a few name fields (any values are fine).

Then create `keystore.properties` in the project folder with:

```properties
storeFile=reelcounter-release.jks
storePassword=<the password you chose>
keyAlias=reelcounter
keyPassword=<the same password>
```

Both files are git-ignored (`*.jks`, `keystore.properties`), so they never reach GitHub.
**Back up both somewhere safe** (for example a USB stick or a password manager).

Without `keystore.properties` the build still works: it signs with the debug key and prints a
warning. That APK installs fine for testing, but switching to your own key later means
uninstalling first.

## 2. Build

For a phone that can't install apps with an accessibility service from a file (reel estimates only):

```bash
./gradlew assembleRelease -PreelCounting=false
```

With exact reel counting (install it over Wireless debugging, see section 4):

```bash
./gradlew assembleRelease
```

The APK is at `app/build/outputs/apk/release/app-release.apk`.

## 3. Install

Copy the APK to the phone and open it, or install from the PC with:

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/release/app-release.apk
```

The release app (`com.reelcounter.app`) is a **separate app** from the debug one
(`com.reelcounter.app.debug`), so both can be installed at once. Sign in to the release app; your
friends and history come back from your account. Then uninstall the old debug app.

## 4. Exact counting: install over Wireless debugging

1. Phone and PC on the same Wi-Fi.
2. Phone: Settings → About phone → Version → tap **Build number** 7 times.
3. Settings → Additional settings → Developer options → turn on **Wireless debugging**, tap it,
   then **Pair device with pairing code**.
4. PC: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" pair <IP:port>` and enter the code.
5. PC: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" connect <IP:port from the main Wireless debugging screen>`.
6. Install with the command in section 3, using the APK built **without** `-PreelCounting=false`.
7. Phone: Settings → Accessibility → turn on **ReelCounter reel counting** (if it says restricted:
   App info → ⋮ → Allow restricted settings).

## Checking a release build

- `aapt2 dump xmltree <apk> --file AndroidManifest.xml | grep -ci accessibility` → `0` for a
  `-PreelCounting=false` build.
- To try a release build against the local backend (`http://127.0.0.1:54321`), plain HTTP must be
  allowed for that test only: copy `app/src/debug/AndroidManifest.xml` and `app/src/debug/res` into
  `app/src/release/`, build, then **delete `app/src/release/` again**. Never ship that build.
