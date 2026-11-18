package kr.sungok.wirelessexplorer;

/**
 * Selected Bluetooth SIG assigned manufacturer-advertisement identifiers.
 * This names an advertised ID, NEVER establishes the radio's actual maker,
 * model, owner, or stable physical identity.
 * Source: Bluetooth SIG Assigned Numbers, Company Identifiers.
 */
public final class BleCompanyNames {
    private BleCompanyNames() {}

    public static String forId(int id) {
        switch (id) {
            case 0x0075: return "Samsung Electronics";
            case 0x004C: return "Apple";
            case 0x0006: return "Microsoft";
            case 0x00E0: return "Google";
            default: return "";
        }
    }

    public static String label(int id) {
        String name = forId(id);
        return name.isEmpty() ? "회사 식별자 미매핑"
                : name + " 광고 회사 ID (기기 제조사 확정 아님)";
    }
}
