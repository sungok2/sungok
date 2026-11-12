package com.galaxyuniversalio;

/** Descriptor-only hints. A USB class does not prove that a model can scan or print. */
public final class UsbDeviceClassifier {
    private UsbDeviceClassifier() { }

    public static final class Result {
        public final boolean stillImage;
        public final boolean printer;
        public final boolean vendorSpecific;

        private Result(boolean stillImage, boolean printer, boolean vendorSpecific) {
            this.stillImage = stillImage;
            this.printer = printer;
            this.vendorSpecific = vendorSpecific;
        }

        public String description() {
            if (stillImage && printer) return "Still Image + Printer 클래스 후보 (기능 미검증)";
            if (stillImage) return "Still Image 클래스 (카메라·이미징 장치 등; 스캔 기능 미검증)";
            if (printer) return "Printer 클래스 (인쇄 기능 미검증)";
            if (vendorSpecific) return "제조사 전용 클래스 (프로토콜 분석 필요)";
            return "미분류 (프로토콜 분석 필요)";
        }
    }

    public static Result classify(int deviceClass, int[] interfaceClasses) {
        boolean image = deviceClass == 0x06;
        boolean printer = deviceClass == 0x07;
        boolean vendor = deviceClass == 0xFF;
        if (interfaceClasses != null) {
            for (int cls : interfaceClasses) {
                image |= cls == 0x06;
                printer |= cls == 0x07;
                vendor |= cls == 0xFF;
            }
        }
        return new Result(image, printer, vendor);
    }

    /** USB endpoint transfer type numeric values, independent of Android SDK. */
    public static String endpointTypeName(int type) {
        switch (type) {
            case 0: return "CONTROL";
            case 1: return "ISOCHRONOUS";
            case 2: return "BULK";
            case 3: return "INTERRUPT";
            default: return "UNKNOWN(" + type + ")";
        }
    }
}
