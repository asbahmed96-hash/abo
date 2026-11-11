# ABO NESMA 🦅 — Android starter project

This repository is the first implementation scaffold for the independent ABO NESMA app.

## Build
- JDK 17
- Android SDK platform 35 and build tools 35
- Gradle 8.9+ (the GitHub workflow provisions Gradle)
- `gradle :app:assembleDebug`

The APK is uploaded by GitHub Actions as `ABO-NESMA-debug-apk`.

## Current implementation
- Kotlin + Jetpack Compose shell with Arabic/English UI toggle.
- PDF import through Android Storage Access Framework.
- PDF text extraction through PdfBox-Android.
- Share-to-app PDF intent handling.
- Local text-file persistence in app-private storage.
- Basic chapter heading detection, extractive sentence ranking, and starter flashcard/question scaffolding.

## Not yet production-complete
- This scaffold does NOT yet include an embedded generative language model or a translation model.
- The current question builder is a placeholder and does not produce validated 20 MCQ + 10 true/false + 5 essay questions with reliable answer keys.
- OCR for scanned/image-only PDFs is not implemented.
- Formula/comparison/definition extraction needs subject-aware parsing and validation.
- Export/share of structured JSON/TXT, persistent library UI, timed exam grading, error review, and model update management remain to be implemented.
- Model files are intentionally not bundled until a suitable license-compatible Arabic/English model is selected and tested on target hardware.

Do not treat this scaffold as the final release APK.
