# STORY ENGLISH — Android build track

**GitHub branch:** `story-english-build` in `sungok2/sungok`.

**Status: build automation prepared; the complete original STORY ENGLISH Android source is NOT in this branch. No APK build success is claimed.**

This branch is isolated from the existing OpenScan 3D Android project in `master`. The root application in the base repository is **not** STORY ENGLISH and must not be built under that name.

## Before running the build

1. Put the authentic STORY ENGLISH source (not a decompiled APK) at `story-english/source/`, preserving the original `android/` subtree: `story-english/source/android/settings.gradle(.kts)`.
2. Do not upload API keys, signing keys, `.env`, personal data, unlicensed adult content, or unpublished licensed footage to this **public** repository. Prefer a private repository before uploading source and review licenses first.
3. Run `python3 story-english/scripts/verify_project.py`.
4. On changes to the `story-english/` subtree or the CI workflow file, GitHub Actions runs a debug build. Source absence fails at the preflight gate and **cannot be reported as a successful APK**.
5. Toolchain: JDK 21, SDK 37, Gradle 9.7.0, based on the historical 3.7.0 source audit. Exact compatibility needs re-verification when source is restored.
6. For this **public** repository, the Actions variable `STORY_ENGLISH_ALLOW_PUBLIC_APK_ARTIFACT=yes` is required to upload APK artifacts after success. Private repositories upload after success automatically.

## Limitations

- Reference 3.7.1 Python/Kotlin snippets are **not** the full 3.7.0 Android source.
- The historical 3.7.0 debug APK is **not** source code.
- This CI does not certify age verification, licensing, human editorial approval, external AI, security, or device behavior.
