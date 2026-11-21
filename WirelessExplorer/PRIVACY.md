# Wireless Explorer — Privacy and Permissions (v0.9.1)

## Data processed
The app uses Android public BLE, Bluetooth Classic, Wi-Fi, Wi-Fi Direct and local network discovery APIs. It may show names, addresses, service identifiers, RSSI, advertisement company IDs and timestamps. Such identifiers can be sensitive. Records are stored locally in Android app-private storage unless users explicitly export them.

## Network behavior
The app contains no account service, ad network, analytics, tracking or cloud telemetry. User-authorized LAN discovery is OFF by default. If explicitly enabled, the app can send a UPnP/SSDP request and bounded TCP handshakes only within the active private Wi-Fi subnet; it refuses active VPN paths. Do not use this feature without the network owner's authorization. It does not attempt authentication, payload collection, or scanning public IP address ranges.

## Permissions
Nearby Devices / Bluetooth Scan & Connect: receive eligible Bluetooth device advertising and access disclosed names. Location and Location Services: required by Android for some BLE/Wi-Fi discovery; the app does not save geographic coordinates. Wi-Fi/Network State: discover nearby Wi-Fi APs, current AP, Direct peers and network availability. INTERNET/Multicast: only for the explicitly enabled local-network probing and discovery protocols, not remote telemetry.

## Storage and deletion
Data is held in private files; Android cloud backup is disabled. The UI can export CSV/JSON/TXT or delete all saved history. Before requesting Android Bluetooth/Wi-Fi/location runtime permissions, the app presents a prominent local-data notice. LAN and UPnP request features require additional authorized-network confirmation. Exported files leave app-private storage and may reveal device MAC/IP addresses. Imported JSON is labeled external history and not current observation. Optional sound is off by default and haptic feedback can be turned off.

## Interpretation and production status
An observed address, advertisement or LAN service does not necessarily represent a unique physical wireless device. BLE address randomization, Android scan limits and silent devices make complete nearby-device enumeration impossible without compatible hardware and cooperation. RSSI is not a location estimate.

This build still requires Samsung Galaxy hardware field validation and stable private release signing for production distribution.
