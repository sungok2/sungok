package android.hardware.usb;
import java.io.ByteArrayOutputStream;
public class UsbDeviceConnection {
    public boolean claimSuccess = true, alternateSuccess = true, closed = false, released = false, failBulk = false;
    public int requestType, request, wValue, wIndex, claimCalls, sendCalls;
    public final ByteArrayOutputStream sent = new ByteArrayOutputStream();
    public byte[] incoming = new byte[0];
    public int incomingOffset = 0;
    public boolean claimInterface(UsbInterface intf, boolean force) { claimCalls++; return claimSuccess; }
    public boolean setInterface(UsbInterface intf) { return alternateSuccess; }
    public boolean releaseInterface(UsbInterface intf) { released = true; return true; }
    public void close() { closed = true; }
    public int controlTransfer(int type, int req, int value, int index, byte[] data, int length, int timeout) {
        requestType = type; request = req; wValue = value; wIndex = index;
        if (req == 0) {
            byte[] id = new byte[]{0, 11, 'M','F','G',':','F','A','K','E',';'};
            System.arraycopy(id, 0, data, 0, id.length);
            return id.length;
        }
        if (req == 1) { data[0] = 0x18; return 1; }
        return -1;
    }
    public int bulkTransfer(UsbEndpoint ep, byte[] bytes, int off, int len, int timeout) {
        sendCalls++;
        if (failBulk) return -1;
        int consumed = Math.min(len, 997);
        if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
            consumed = Math.min(consumed, incoming.length - incomingOffset);
            if (consumed <= 0) return -1;
            System.arraycopy(incoming, incomingOffset, bytes, off, consumed);
            incomingOffset += consumed;
        } else sent.write(bytes, off, consumed);
        return consumed;
    }
}
