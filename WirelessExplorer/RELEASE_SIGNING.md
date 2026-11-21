# Owner-Signed Production Release Gate (Wireless Explorer)

The standard CI artifact is a DEBUG-signed test APK, plus an unsigned AAB structure validation. Neither artifact is store-sale ready. GitHub runners can generate different debug signing keys, so it may not install over a previous APK. Export a JSON backup before uninstalling a version.

## Required owner action

Generate a persistent Android signing key on a trusted local computer, back it up securely, and add the following FOUR repository Actions secrets under Settings > Secrets and variables > Actions:

- WE_RELEASE_KEYSTORE_B64 — Base64 of the binary .jks signing file
- WE_RELEASE_STORE_PASSWORD — keystore password
- WE_RELEASE_KEY_ALIAS — signing key alias
- WE_RELEASE_KEY_PASSWORD — signing key password

Run the manually triggered GitHub workflow “Wireless Explorer Signed Release” on a reviewed commit. It will fail if secrets are not present, use Gradle to run tests/lint and build a persistently signed APK and Android App Bundle (AAB), verify signatures and SHA-256 checksums for both, publish both artifacts, and delete its runner-local keystore copy.

Never commit the private .jks file or passwords. Store an independently encrypted offline backup. The exact SAME key is needed to distribute upgrades without uninstalling.

## Additional release acceptance criteria

- Test discovery, Wi-Fi permissions, Bluetooth, LAN consent and export/import on a Samsung Galaxy.
- Verify that screen rotation, large font sizes, TalkBack, optional tones/quiet mode, battery use and recovery from backgrounding work on real hardware.
- Run release APK installation and upgrade tests using the same release key.
- Verify public privacy policy, advertised hardware support and any applicable distribution-channel requirements.
- Do not claim total nearby physical wireless-device coverage, because Android public APIs do not expose all radios.

Reference: https://developer.android.com/studio/publish/app-signing

Current state: secure release workflow prepared, but a stable owner signing key has not been configured or used in this session. Standard CI builds and emulator tests are verifiable; the owner-signed workflow is intentionally blocked until the private secrets exist.
