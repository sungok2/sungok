# Manufacturer driver integration over Galaxy USB OTG

Research date: 2026-11-18. This document distinguishes Android print-service reuse from the app's own USB transports. Brand alone is not a compatibility profile; exact model, firmware, interface descriptors, driver version, and a physical test are required.

## Integration design

### Printing

The `PDF 파일 선택 후 인쇄` workflow passes a PDF to Android `PrintManager`. Android's system print UI can dispatch it to an installed and enabled `PrintService`; the manufacturer service owns its driver and transport. The v0.11.0 UI adds a shortcut to Android print settings so the user can enable the service before reconnecting the printer.

This reuses an installed Android print driver. It does not install or execute Windows/macOS printer drivers inside the APK, and it cannot force a `PrintService` to support USB if the service does not implement that model/transport.

The app's direct USB actions remain protocol-gated:

- printer status and pre-encoded PDL RAW transfer for compatible USB Printer Class interfaces;
- direct PDF only for qualifying IPP-over-USB devices that advertise `application/pdf`;
- no conversion of arbitrary PDF/HWP/HWPX/JPEG into each manufacturer's proprietary printer language.

### Epson WorkForce Pro WF-C5390 profile (v0.12.0)

The model profile is fetched on user request from this repository as constrained text data. The app accepts only allowlisted model/PDL/resolution/page/job-size values and contains no downloaded executable driver. Its built-in Android `PdfRenderer` converts PDF pages to RGB raster and PostScript 3 Level 3 `colorimage` streams; compressed raster data is streamed to USB Printer Class protocol 1/2 Bulk OUT. The profile is protocol-reviewed against Epson's published PDL support but remains **not hardware-tested** until a Galaxy and WF-C5390 print a known test sheet over the intended OTG cable. If the printer uses another USB interface protocol, the direct route stops before sending.

This direct path uses Epson's published PostScript support, not Epson's private driver. It does not configure the Epson Print Enabler for USB: Epson's guide documents that service for devices on the same wireless network. The current app limits this renderer to 150 dpi, 20 PDF pages, and a 64 MiB generated job; it reports USB transfer completion, not paper output success.

### Scanning

Android exposes a USB Host API for enumerating a device, obtaining user permission, and communicating with its interfaces/endpoints. That is a transport API, not a universal scanner-driver catalog. The app's current direct USB scan route supports its implemented IPP-over-USB eSCL profile. PTP is JPEG import from a compatible still-image device, not general flatbed or ADF scanner support.

A vendor scan app's private USB driver cannot be called by another app unless the manufacturer provides a documented public SDK/API or an explicit Android intent/URI contract. Without such a contract, the safe choices are: use the vendor app as a separate workflow, add a public SDK integration for a supported model, or implement and physically test that model's protocol. Do not claim that installing a PC driver makes it available to this Android app.

## Manufacturer evidence and current implication

| Manufacturer | Official Android evidence | App implication |
|---|---|---|
| HP | HP documents USB OTG printing with its HP Print Service Plugin. Its Android setup instructions condition USB printing on printer support for PCL3 or PCL5 and OTG-capable phone. | The Android `PrintManager` route can hand the PDF to HP's installed service. Confirm the exact printer model and service behavior. |
| Samsung | HP-hosted Samsung Print Service Plugin FAQ says USB/OTG printers are not detected by that plugin. Samsung Mobile Print guidance describes USB sharing through a PC setup in the documented workflow. | Do not treat Samsung's print plugin as a direct Galaxy-to-printer USB driver. Test a model's standard PDL/IPP route or a documented Android driver separately. |
| Epson | Epson Print Enabler documentation describes Wi-Fi printing; Smart Panel scanning instructions describe network/direct Wi-Fi scanning. | These documents do not establish direct USB OTG support. A model-specific public Android USB driver/SDK or standard USB profile is needed. |
| Canon | Canon Print Service instructions for the reviewed model specify wireless LAN; Canon mobile scanning instructions require the printer and phone on the same network. | Do not infer USB OTG support from the Canon Android app. Confirm a documented model-specific USB pathway before listing support. |

## Verification gate for each requested model

Record and verify separately for every device:

1. Manufacturer, full model number, firmware, USB VID/PID, class/subclass/protocol, interfaces and endpoints.
2. Galaxy model/Android version, USB-C OTG adapter/cable, power needs, USB permission result, disconnect/reconnect behavior.
3. Print: installed/active Android PrintService and version, selected USB printer, known-good one-page document, page size/color/duplex, output evidence.
4. Scan: specific driver/app/SDK path, flatbed or ADF, one-page and multi-page output, PDF/JPEG integrity, cancel and disconnect handling.
5. Result status: `detected`, `protocol-reviewed`, or `hardware-tested`. Do not mark a model hardware-tested without physical evidence.

## Official sources

- Android USB Host APIs: https://developer.android.com/develop/connectivity/usb/host
- Android print API / PrintManager: https://developer.android.com/reference/android/print/package-summary
- HP Android Print Service Plugin and PCL3/PCL5 USB OTG condition: https://support.hp.com/us-en/document/ish_1870466-1870510-16
- HP USB OTG setup: https://support.hp.com/kr-ko/document/c05574946
- Samsung Print Service Plugin USB/OTG FAQ: https://support.hp.com/kr-ko/document/c06081111
- Samsung Mobile Print USB/PC-sharing workflow: https://support.hp.com/gb-en/document/c06117046
- Epson Print Enabler: https://epson.com/Support/Printers/Mobile-and-Cloud-Solutions/Epson-Print-Enabler-(Android-Print)/s/SPT_ENABLER-NS
- Epson Smart Panel scan workflow: https://files.support.epson.com/docid/cpd6/cpd63958/source/spcs/source/scanners/source/scanning_software/tasks/starting_scan_smartpanel.html
- Canon Android print manual (E470 example): https://ij.manual.canon/ij/webmanual/Manual/All/E470%20series/EN/AFG/afg_p_xx_xx_03.html
- Canon Android scan instructions: https://ij.manual.canon/ij/webmanual/WebPortal/APN/apn_smartscan.html?lng=en
