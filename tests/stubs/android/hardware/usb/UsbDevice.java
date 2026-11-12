package android.hardware.usb;
public final class UsbDevice {
    private final UsbInterface[] interfaces;
    public UsbDevice(UsbInterface... interfaces) { this.interfaces = interfaces; }
    public int getInterfaceCount() { return interfaces.length; }
    public UsbInterface getInterface(int i) { return interfaces[i]; }
}
