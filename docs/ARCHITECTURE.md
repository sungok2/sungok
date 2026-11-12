> **Historical M0.3 reference** — For the latest M0.5 status, read `docs/M0_5_VERIFICATION.md` and `docs/M0_5_CHANGELOG.md`.

# Galaxy Universal I/O M0.3 — architecture

## Read-only inventory

```
USB Host manager getDeviceList
  -> Device / Interface / Endpoint descriptors
  -> UsbDeviceClassifier.classify (hints, not verified drivers)
  -> UsbProfileCatalog.find (exact VID:PID, registry currently empty)
  -> MainActivity read-only report
  -> Storage Access Framework UTF-8 TXT export
```

## Explicit Android permission

```
User selects a USB device
  -> UsbManager.hasPermission
  -> if needed requestPermission via explicit mutable PendingIntent
  -> non-exported UsbPermissionReceiver
  -> package-scoped notification
  -> MainActivity independently rechecks real UsbManager.hasPermission
```

A broadcast boolean is not trusted as the permission source. Device ID and path are **not stable across reconnects**.

## USB Printer Class 1.1

```
User presses printer status button
  -> requires class 07 / subclass 01 / protocol 01..03 and USB permission
  -> background worker, openDevice, claimInterface(force=false)
  -> GET_DEVICE_ID, GET_PORT_STATUS (class control requests)
  -> releaseInterface, close
  -> display response or clear error

User presses RAW file send
  -> requires class 07 / subclass 01 / protocol 01 or 02 / Bulk OUT
  -> .prn/.pcl/.ps file picker
  -> device identity and USB permission rechecked
  -> explicit confirmation dialog
  -> full read with 8 MiB hard ceiling BEFORE transmission
  -> revalidate device + permission
  -> open, claim, bounded 8192-byte Bulk OUT chunks, release, close
```

No PDF/HWP rendering, IPP, printer-language conversion or generic driver installation. USB Printer Class is a transport, not a PDL. USB protocol 03 is detected but its IEEE 1284.4 framing has not been implemented; RAW is disabled.

Scanner image acquisition is **not implemented**. USB Still Image class 06 may represent cameras; other real scanners use vendor-specific interfaces. Per-device/firmware protocol evidence and physical image acquisition are prerequisites.

## Interface bounds / security

- Android public USB Host APIs only, no privileged/root access, no forced detach (`claimInterface(..., false)`).
- No automatic outbound raw data, no network access, serial numbers excluded by default.
- USB timeout 3000 ms per control/bulk operation; raw file max 8 MiB.
- Print transfer byte count is not proof of successful paper output.
- Runtime USB errors, bus/driver conflicts and scanner support need physical testing.

## M0.3 추가 계층
- `AndroidPdfPrintAdapter`: Android Print Framework PDF 스트리밍 복사 (인쇄 서비스 선택 필요)
- `PdfSpoolPreflight`: 입력 PDF 헤더, 32MiB 크기, 취소 검증
- `PtpProtocol`: PTP 컨테이너 인코딩/디코딩, 객체 핸들·정보·JPEG 제한
- `PtpUsbTransport`: PTP 표준 Still Image 6/1/1 인터페이스의 Bulk IN/OUT 요청·JPEG 단일 입력 (실물 미검증)
- USB 7/1/4 IPP-over-USB: 탐지만 가능, RAW 비활성화
- 기존 문서의 generic scanner acquisition not implemented는 여전히 유효하며 PTP는 카메라 계열 제한적 입력 경로
