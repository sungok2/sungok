package android.hardware.usb;
public final class UsbInterface {
    private final int id, alt, cls, sub, proto;
    private final UsbEndpoint[] endpoints;
    public UsbInterface(int id, int alt, int cls, int sub, int proto, UsbEndpoint... endpoints) {
        this.id = id; this.alt = alt; this.cls = cls; this.sub = sub; this.proto = proto;
        this.endpoints = endpoints;
    }
    public int getId() { return id; }
    public int getAlternateSetting() { return alt; }
    public int getInterfaceClass() { return cls; }
    public int getInterfaceSubclass() { return sub; }
    public int getInterfaceProtocol() { return proto; }
    public int getEndpointCount() { return endpoints.length; }
    public UsbEndpoint getEndpoint(int i) { return endpoints[i]; }
}
