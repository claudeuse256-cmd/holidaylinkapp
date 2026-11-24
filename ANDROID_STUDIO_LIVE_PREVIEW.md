# Android Studio setup and live preview

This project is a native Kotlin + Jetpack Compose app. It builds from a normal
Android Studio install; no Flutter and no web server are involved.

## What is already set up on this PC

| Item | Location / value |
| --- | --- |
| JDK for Gradle (AGP 8.7 needs 17) | `C:\Users\don mahe ug\.jdks\jdk-17.0.20.1+1` |
| Global Gradle Java home | `C:\Users\don mahe ug\.gradle\gradle.properties` (`org.gradle.java.home`) |
| Gradle wrapper | `android/gradlew.bat` + `android/gradle/wrapper` (Gradle 8.9) |
| Android SDK | `C:\Users\don mahe ug\AppData\Local\Android\Sdk` (Platform 35, Build-Tools 35.0.0) |
| Android Studio Gradle JDK | `android/.gradle/config.properties` points at JDK 17 |

Android Studio itself starts on its bundled Java 25; that is fine. Only the
Gradle build runs on JDK 17, which AGP 8.7.3 requires.

## Open the project

1. Android Studio → **File → Open**.
2. Select the `android` folder (the one containing `settings.gradle.kts`),
   not the repository root.
3. Wait for **Gradle Sync** to finish. It should report `BUILD SUCCESSFUL`;
   the first sync downloads AndroidX/Compose/Firebase libraries.

## See the UI preview (no emulator needed)

1. Open
   `app/src/main/java/org/holidaylink/app/ui/ComposePreviewGallery.kt`.
2. Switch the editor to **Split** or **Design** (top-right editor buttons).
3. Pick a variant in the preview panel:
   **HolidayLink · Chats · Light** or **HolidayLink · Chats · Dark**.
4. Edits to that file redraw the preview on the next keystroke.

The gallery is sample data only. It does not start Firebase, the backend, or
permissions, so it is the fastest way to check spacing, type, and colors.

### Preview without Android Studio

`./gradlew recordPaparazziDebug` renders the same composables to PNG files in
`app/src/test/snapshots/images/`. This runs layoutlib on the JVM, so it also
works when no device is available. The Paparazzi plugin is declared in
`android/app/build.gradle.kts`; the test lives in
`android/app/src/test/java/org/holidaylink/app/PreviewSnapshotTest.kt`.

## Build the installable APK

```bash
cd android
./gradlew assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk` (verified building
on this machine).

## Run the real app (functions, Firebase, chats)

The app needs a device or an emulator. **An emulator cannot run on this
machine as configured**: it reports 3.7 GB total RAM (4 GB is the emulator
minimum) and Windows reports *Virtualization Enabled In Firmware: No*, so
there is no hypervisor for hardware acceleration.

Options, in order of practicality:

1. **Real phone over USB** (recommended)
   - Enable Developer options → USB debugging on the phone.
   - Connect it, accept the RSA prompt, then `adb devices` should list it.
   - In Android Studio pick the phone in the device selector and press Run.
2. **Enable the emulator**
   - Turn on Intel VT-x/AMD-V in the BIOS/UEFI, and on a machine with at
     least 8 GB RAM.
   - Then install the Android Emulator hypervisor driver from the SDK Manager
     (SDK Tools tab), or enable Windows Hypervisor Platform.

## Live Edit

With the app running on a phone or emulator, Android Studio's **Live Edit**
(Compose only) pushes supported UI edits without a full restart. Dependency,
manifest, and initialization changes still need a rebuild.

## Notes

- `google-services.json` contains both `org.holidaylink.app` (this app) and a
  web client; keep the file private and review Firebase rules before release.
- `HL_API_BASE_URL` can be overridden with `-PHL_API_BASE_URL=...` or in
  `android/gradle.properties`.
