package android.hardware.usb;
public class UsbManager {
    public boolean permitted = true;
    public UsbDeviceConnection connection;
    public boolean hasPermission(UsbDevice device) { return permitted; }
    public UsbDeviceConnection openDevice(UsbDevice device) { return connection; }
}
