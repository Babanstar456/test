# DIPTI Medical – Android app

Native Android wrapper for https://dipti-medical-app.vercel.app/ with the DIPTI logo as
app icon and splash. Builds a **signed release APK** (plus an AAB for Play Store).

Included: splash screen, progress bar, pull-to-refresh, back-button navigation
(double-back to exit), branded offline screen with Retry, file uploads (prescriptions etc.),
downloads, login cookies kept, tel/mailto/WhatsApp/UPI links open the native apps,
links to the site open in the app.

## Option A – GitHub Actions (no Android Studio needed)
1. Create a **private** GitHub repo and push this folder (the .gitignore keeps the key out).
2. Repo → Settings → Secrets and variables → Actions → add the 4 secrets listed in
   `SIGNING_SECRETS.txt`.
3. Actions tab → "Build release APK" → Run workflow (it also runs on every push to main).
4. When it finishes (~4 min), download the `DIPTI-Medical-release` artifact →
   `DIPTI-Medical-release.apk`.

## Option B – Android Studio (local)
1. Open the folder in Android Studio (Koala or newer), let Gradle sync.
2. Terminal: `./gradlew assembleRelease`  (if there is no `gradlew`, run `gradle wrapper` once,
   or use Build → Generate Signed App Bundle / APK).
3. APK: `app/build/outputs/apk/release/app-release.apk`
   `keystore.properties` + `dipti-release.jks` are already in the folder, so it signs automatically.

## Changing things
- URL: `app/src/main/res/values/strings.xml` → `start_url` (also update the host in AndroidManifest.xml)
- App name: same file → `app_name`
- New version: bump `versionCode` and `versionName` in `app/build.gradle.kts`
- Package id: `com.dipti.medical` (change before first Play Store upload if needed)
