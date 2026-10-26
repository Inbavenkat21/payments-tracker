# Flux — Personal Payment Tracker (Android)

A native Android starter app with a dark premium fintech UI, local SQLite records, manual transaction entry, basic SMS inbox import, search/filter, source summaries, and insights.

## No Android Studio? Build the APK in GitHub Actions

This repository includes `.github/workflows/build-apk.yml` so you can build the APK in the cloud.

1. Sign in to GitHub and create a **new empty repository** (for example, `payment-tracker`).
2. Extract this ZIP on your computer.
3. Upload the extracted project files and folders to the repository root. Make sure `.github/workflows/build-apk.yml` is included. GitHub's web interface can upload files, though uploading a folder tree is easiest with GitHub Desktop.
4. Open the repository's **Actions** tab and select **Build Payment Tracker APK**.
5. Click **Run workflow**. Wait for the run to finish successfully.
6. Open the completed run and download the artifact named **PaymentTracker-debug-apk**.
7. Extract the artifact ZIP. It contains `app-debug.apk`. Transfer it to your Android phone and install it. Android may ask you to allow installation from that source.

GitHub account and repository setup are required; this environment cannot compile an Android APK directly because it has no Android SDK/build tools and cannot download them.

## Features in this starter

- Premium dark fintech styling with lime accents and responsive transaction panels
- Manually add income and expense records
- Private on-device SQLite storage
- Search and filter transactions
- Account/source summaries and category overview
- Opt-in SMS inbox import after runtime permission approval
- Duplicate SMS import prevention
- Clear stored transactions

## Important limitations

- The app is an early starter, not a production banking app.
- The dashboard displays only records saved in this app, not a real bank balance.
- SMS parsing is heuristic and must be reviewed for accuracy; bank message formats differ.
- It scans the existing inbox only when you tap Import SMS. It does not run a background SMS listener.
- SMS access is sensitive. Publishing on Google Play is subject to Google's restricted SMS permission policies; an APK built for personal sideloading is not equivalent to Play Store approval.
- No real bank API or Google Pay/PhonePe private data integration is included.
- Keep your GitHub repository private if you do not want to publish the source code publicly.

## Technical details

- Kotlin / native Android views
- Android Gradle Plugin 8.7.3
- Kotlin Gradle Plugin 2.0.21
- Compile SDK 35, min SDK 26
- SQLite database in app-private storage
