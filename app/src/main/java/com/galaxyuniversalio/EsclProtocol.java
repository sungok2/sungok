package com.galaxyuniversalio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Bounded eSCL capability negotiation. Sources are validated independently. */
public final class EsclProtocol {
    public static final int MAX_XML_BYTES = 512 * 1024;
    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_TOTAL_BYTES = 32 * 1024 * 1024;
    public static final int MAX_PAGES = 12;
    private EsclProtocol() { }

    public static final class Source {
        public final int dpi, width, height;
        public final String colorMode, format;
        Source(int dpi, int width, int height, String colorMode, String format) {
            this.dpi = dpi; this.width = width; this.height = height;
            this.colorMode = colorMode; this.format = format;
        }
    }
    public static final class Capabilities {
        public final boolean platen, feeder;
        public final int dpi, width, height;
        public final String colorMode;
        private final Source platenSource, feederSource;
        Capabilities(Source platen, Source feeder) {
            platenSource = platen;
            feederSource = feeder;
            this.platen = platen != null;
            this.feeder = feeder != null;
            Source preferred = platen != null ? platen : feeder;
            this.dpi = preferred.dpi;
            this.colorMode = preferred.colorMode;
            this.width = preferred.width;
            this.height = preferred.height;
        }
        public Source source(boolean feeder) { return feeder ? feederSource : platenSource; }
    }

    public static Capabilities parseCapabilities(byte[] xml) throws IOException {
        if (xml == null || xml.length == 0 || xml.length > MAX_XML_BYTES)
            throw new IOException("eSCL capability XML size invalid");
        String text = new String(xml, StandardCharsets.UTF_8).toUpperCase(Locale.US);
        if (text.contains("<!DOCTYPE") || text.contains("<!ENTITY"))
            throw new IOException("DTD/entity declarations forbidden in scanner capabilities");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
            catch (javax.xml.parsers.ParserConfigurationException ignored) { }
            try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); }
            catch (javax.xml.parsers.ParserConfigurationException ignored) { }
            try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); }
            catch (javax.xml.parsers.ParserConfigurationException ignored) { }
            try { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); }
            catch (javax.xml.parsers.ParserConfigurationException ignored) { }
            try { factory.setXIncludeAware(false); }
            catch (UnsupportedOperationException ignored) { }
            factory.setExpandEntityReferences(false);
            try { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); }
            catch (IllegalArgumentException ignored) { }
            try { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); }
            catch (IllegalArgumentException ignored) { }
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            if (doc.getDocumentElement() == null ||
                    !"ScannerCapabilities".equals(doc.getDocumentElement().getLocalName()))
                throw new IOException("Not an eSCL ScannerCapabilities document");
            Source platen = source(doc, "PlatenInputCaps");
            Source feeder = source(doc, "AdfSimplexInputCaps");
            if (feeder == null) feeder = source(doc, "AdfInputCaps");
            // Do not infer ADF support from Platen capabilities or from an empty ADF tag.
            if (platen == null && feeder == null)
                throw new IOException("No compatible platen or simplex ADF profile");
            return new Capabilities(platen, feeder);
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Invalid or insecure eSCL capabilities XML", e); }
    }

    private static Source source(Document doc, String name) {
        NodeList nodes = doc.getElementsByTagNameNS("*", name);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element src = (Element) nodes.item(i);
            int width = Math.min(2480, dimension(src, "MaxWidth", 2480));
            int height = Math.min(3508, dimension(src, "MaxHeight", 3300));
            if (width < 1000 || height < 1000) continue;
            NodeList profiles = src.getElementsByTagNameNS("*", "SettingProfile");
            if (profiles.getLength() == 0) {
                Source candidate = profile(src, width, height);
                if (candidate != null) return candidate;
            } else {
                for (int j = 0; j < profiles.getLength(); j++) {
                    Source candidate = profile((Element) profiles.item(j), width, height);
                    if (candidate != null) return candidate;
                }
            }
        }
        return null;
    }

    private static Source profile(Element scope, int width, int height) {
        String format = value(scope, "DocumentFormat", "image/jpeg")
                || value(scope, "DocumentFormatExt", "image/jpeg") ? "image/jpeg"
                : value(scope, "DocumentFormat", "image/png")
                || value(scope, "DocumentFormatExt", "image/png") ? "image/png" : null;
        String color = value(scope, "ColorMode", "RGB24") ? "RGB24"
                : value(scope, "ColorMode", "Grayscale8") ? "Grayscale8" : null;
        if (format == null || color == null) return null;
        int dpi = 0, nextHigher = Integer.MAX_VALUE;
        NodeList xs = scope.getElementsByTagNameNS("*", "XResolution");
        for (int i = 0; i < xs.getLength(); i++) {
            try {
                int x = Integer.parseInt(xs.item(i).getTextContent().trim());
                if (x <= 0 || x > 600) continue;
                // eSCL specifies the same horizontal and vertical resolution for this pipeline.
                Node pair = xs.item(i).getParentNode();
                if (pair instanceof Element && "DiscreteResolution".equals(pair.getLocalName())) {
                    int y = dimension((Element) pair, "YResolution", -1);
                    if (y != x) continue;
                }
                if (x <= 300 && x > dpi) dpi = x;
                else if (x > 300 && x < nextHigher) nextHigher = x;
            } catch (NumberFormatException ignored) { }
        }
        if (dpi == 0 && nextHigher != Integer.MAX_VALUE) dpi = nextHigher;
        return dpi == 0 ? null : new Source(dpi, width, height, color, format);
    }

    private static int dimension(Element parent, String local, int fallback) {
        NodeList values = parent.getElementsByTagNameNS("*", local);
        for (int i = 0; i < values.getLength(); i++) {
            try {
                int value = Integer.parseInt(values.item(i).getTextContent().trim());
                if (value > 0 && value <= 20000) return value;
            } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }
    private static boolean value(Element scope, String local, String match) {
        NodeList values = scope.getElementsByTagNameNS("*", local);
        for (int i = 0; i < values.getLength(); i++)
            if (match.equalsIgnoreCase(values.item(i).getTextContent().trim())) return true;
        return false;
    }

    public static byte[] settings(Capabilities caps, boolean feeder) throws IOException {
        if (caps == null || caps.source(feeder) == null)
            throw new IOException("Requested document source has no compatible scan profile");
        Source src = caps.source(feeder);
        String input = feeder ? "Feeder" : "Platen";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<scan:ScanSettings xmlns:scan=\"http://schemas.hp.com/imaging/escl/2011/05/03\" "
                + "xmlns:pwg=\"http://www.pwg.org/schemas/2010/12/sm\">"
                + "<pwg:Version>2.0</pwg:Version>"
                + "<pwg:ScanRegions><pwg:ScanRegion>"
                + "<pwg:ContentRegionUnits>escl:ThreeHundredthsOfInches</pwg:ContentRegionUnits>"
                + "<pwg:XOffset>0</pwg:XOffset><pwg:YOffset>0</pwg:YOffset>"
                + "<pwg:Width>" + src.width + "</pwg:Width><pwg:Height>" + src.height + "</pwg:Height>"
                + "</pwg:ScanRegion></pwg:ScanRegions>"
                + "<pwg:InputSource>" + input + "</pwg:InputSource>"
                + "<scan:ColorMode>" + src.colorMode + "</scan:ColorMode>"
                + "<pwg:DocumentFormat>" + src.format + "</pwg:DocumentFormat>"
                + "<scan:DocumentFormatExt>" + src.format + "</scan:DocumentFormatExt>"
                + "<scan:XResolution>" + src.dpi + "</scan:XResolution>"
                + "<scan:YResolution>" + src.dpi + "</scan:YResolution>"
                + "</scan:ScanSettings>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public static String jobPath(String location, String expectedOrigin) throws IOException {
        if (location == null || location.isEmpty() || location.length() > 2048)
            throw new IOException("Missing eSCL job Location");
        try {
            URI u = new URI(location.trim());
            if (u.getFragment() != null || u.getRawQuery() != null || u.getUserInfo() != null)
                throw new IOException("Invalid scan job URI");
            if (u.isAbsolute()) {
                URI origin = new URI(expectedOrigin);
                if (!u.getScheme().equalsIgnoreCase(origin.getScheme())
                        || !u.getHost().equalsIgnoreCase(origin.getHost())
                        || effectivePort(u) != effectivePort(origin))
                    throw new IOException("Cross-origin scan job Location rejected");
            }
            String path = u.getRawPath();
            if (path == null || !path.startsWith("/eSCL/ScanJobs/"))
                throw new IOException("Scan job outside /eSCL/ScanJobs");
            String opaque = path.substring("/eSCL/ScanJobs/".length());
            if (opaque.isEmpty() || opaque.contains("/") || opaque.contains("..")
                    || opaque.contains("%") || opaque.contains("\\") || opaque.length() > 200)
                throw new IOException("Unsafe scan job identifier");
            for (char c : opaque.toCharArray())
                if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':'))
                    throw new IOException("Unsafe scan job identifier");
            return path;
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Malformed scan job URI", e); }
    }
    private static int effectivePort(URI u) {
        return u.getPort() != -1 ? u.getPort() : "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
    }

    public static String requireImage(byte[] body, String contentType) throws IOException {
        if (contentType == null) throw new IOException("Missing image content type");
        String type = contentType.toLowerCase(Locale.US).split(";", 2)[0].trim();
        if (body == null || body.length < 4 || body.length > MAX_IMAGE_BYTES)
            throw new IOException("Invalid image data size");
        if (type.equals("image/jpeg")) {
            if ((body[0] & 255) != 255 || (body[1] & 255) != 216
                    || (body[body.length - 2] & 255) != 255 || (body[body.length - 1] & 255) != 217)
                throw new IOException("Bad or truncated JPEG data");
        } else if (type.equals("image/png")) {
            validatePng(body);
        } else throw new IOException("Unsupported image content type");
        return type;
    }
    public static void requireJpeg(byte[] body, String contentType) throws IOException {
        if (!"image/jpeg".equals(requireImage(body, contentType)))
            throw new IOException("Expected JPEG, received other image type");
    }
    public static String imageMimeFromBytes(byte[] body) throws IOException {
        if (body == null || body.length < 4) throw new IOException("Empty image");
        if ((body[0] & 255) == 255 && (body[1] & 255) == 216)
            return requireImage(body, "image/jpeg");
        return requireImage(body, "image/png");
    }
    private static void validatePng(byte[] png) throws IOException {
        final byte[] magic = {(byte)137,80,78,71,13,10,26,10};
        if (png.length < 45) throw new IOException("Truncated PNG");
        for (int i = 0; i < magic.length; i++)
            if (png[i] != magic[i]) throw new IOException("Invalid PNG signature");
        int pos = 8;
        int chunks = 0;
        boolean seenIhdr = false, seenIdat = false, seenIend = false;
        while (pos <= png.length - 12 && ++chunks < 8192) {
            long len = u32(png, pos);
            if (len > png.length - (long) pos - 12) throw new IOException("Truncated PNG chunk");
            int dataStart = pos + 8;
            int c = dataStart + (int) len;
            String tag = new String(png, pos + 4, 4, StandardCharsets.US_ASCII);
            CRC32 checksum = new CRC32();
            checksum.update(png, pos + 4, (int) len + 4);
            if (checksum.getValue() != u32(png, c)) throw new IOException("PNG CRC mismatch");
            if (chunks == 1) {
                if (!"IHDR".equals(tag) || len != 13) throw new IOException("Missing PNG IHDR");
                int w = (int) u32(png, dataStart), h = (int) u32(png, dataStart + 4);
                if (w <= 0 || h <= 0 || w > 30000 || h > 30000)
                    throw new IOException("Unsafe PNG dimensions");
                seenIhdr = true;
            }
            if ("IDAT".equals(tag)) seenIdat = true;
            if ("IEND".equals(tag)) {
                if (len != 0 || !seenIdat || c + 4 != png.length)
                    throw new IOException("Malformed PNG end");
                seenIend = true;
                break;
            }
            pos = c + 4;
        }
        if (!seenIhdr || !seenIend) throw new IOException("Incomplete PNG image");
    }
    private static long u32(byte[] b, int i) {
        return ((long)(b[i]&255)<<24) | ((long)(b[i+1]&255)<<16)
                | ((long)(b[i+2]&255)<<8) | (b[i+3]&255);
    }
}
