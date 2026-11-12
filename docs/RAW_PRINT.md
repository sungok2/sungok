# RAW printer transfer — scope and safety

## What it does

1. Android USB Host enumerates interfaces. Select a USB Printer Class 07/subclass 01, protocol 01 (unidirectional) or 02 (bidirectional), with a Bulk OUT endpoint.
2. User explicitly obtains Android permission, chooses `.prn`, `.pcl` or `.ps`, sees a confirmation dialog.
3. Entire source file is read into memory before USB transfer, with a hard cap of **8 MiB**.
4. App opens the USB device, claims just the printer interface with `force=false`, and transfers raw bytes in chunks of at most 8192. The interface/connection is released even if transfer fails.
5. Result indicates **USB bytes transferred**, not successful paper output.

## What it does not do

- No PDF/HWP/HWPX/JPG/PNG conversion or printing these formats as-is.
- No guaranteed PDL matching: `.prn` can be Windows-spool data that a given printer cannot interpret.
- No driver deployment, USB IPP, IEEE 1284.4 channel framing, vendor-specific printer protocols, print-job cancellation, printer spooler or printed-page acknowledgement.
- No unrequested transmission: status requests and RAW send each require a user action and USB permission.

## Read-only status details

USB Printer Class 1.1 class-specific controls (USB-IF):

- `GET_DEVICE_ID`: `bmRequestType=0xA1`, `bRequest=0`, `wValue=0` (configuration index), `wIndex=(bInterfaceNumber<<8)|bAlternateSetting`.
- `GET_PORT_STATUS`: `bmRequestType=0xA1`, `bRequest=1`, `wValue=0`, `wIndex=bInterfaceNumber`, 1 byte with paper-empty (bit5), selected (bit4), not-error (bit3).
- The response to GET_DEVICE_ID is a two-byte big-endian length followed by the IEEE 1284 ID string.
- Status bits may not reflect all mechanical conditions; devices can return benign default bits.

Caution: some composite devices, print languages, firmware versions or Android USB drivers require vendor-specific command sets. Test print only with hardware you own/are authorized to use. Begin with a supported device and a known-good printer-language file; unexpected pages/characters are possible when command language mismatches.

### Optional fixture

`samples/POSTSCRIPT_ONLY_TEST.ps` is a minimal PostScript language demonstration file; it is **NOT** safe to send to an arbitrary printer. Use it only when the printer's `CMD`/`COMMAND SET` or manufacturer manual confirms PostScript compatibility and you consent to a physical page.

## Technical sources

- USB-IF Printer Class Definition 1.1: https://www.usb.org/sites/default/files/usbprint11a021811.pdf
- Android UsbDeviceConnection: https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection
- Android USB Host overview: https://developer.android.com/develop/connectivity/usb/host

## M0.3 참고
- PDF는 RAW로 전송하지 않습니다. PDF 시스템 인쇄 버튼은 별도의 Android Print Framework를 사용합니다.
- USB protocol 7/1/4(IPP-over-USB)는 탐지만 구현하고 RAW를 허용하지 않습니다.
