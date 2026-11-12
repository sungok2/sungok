# NetShare P2P M8 isolated debug build

This branch preserves the existing `sungok2/sungok` app. M8 conditional source is stored as `M8_build_source.zip` and is expanded by a separate GitHub Actions workflow.

- Package: `org.example.netsharep2p.conditional`.
- Source archive is a minimal 16-file build subset of the previously generated M8 archive.
- This is only a debug APK build, not proof of functional Wi-Fi Direct internet sharing.
- App intentionally does not create a Wi-Fi Direct group and must not use Android Hotspot/SoftAP/Tethering.
- Even successful CI cannot establish that an existing Wi-Fi Direct connection never invokes internal Android Tethering.
- The executable needs a preexisting, separately verified P2P Group Client link to run the conditional proxy.
