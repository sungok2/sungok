package kr.sungok.wirelessexplorer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Evidence-based report; never infers an impossible "total physical device" count. */
public final class InventoryReport {
    private InventoryReport() { }

    public static String generate(String generatedAt, int radio, int network, int total,
                                  int sharedMacCandidates, Map<String,String> radioStates,
                                  String platform, boolean extendedLan) {
        return generate(generatedAt, radio, 0, network, total,
                sharedMacCandidates, radioStates, platform, extendedLan);
    }

    public static String generate(String generatedAt, int radio, int lan, int network, int total,
                                  int sharedMacCandidates, Map<String,String> radioStates,
                                  String platform, boolean extendedLan) {
        StringBuilder b = new StringBuilder();
        b.append("WIRELESS EXPLORER · 무선 탐색 점검 보고서\n");
        b.append("생성 시각: ").append(generatedAt).append('\n');
        b.append("실행 환경: ").append(platform).append('\n');
        b.append("앱 버전: 0.9.1\n\n");
        b.append("[탐지 항목]\n");
        b.append("이번 탐색 무선 주소/접속점: ").append(radio).append("건\n");
        b.append("이번 탐색 LAN TCP 응답 주소: ").append(lan).append("건 (유·무선 구분 불가)\n");
        b.append("이번 탐색 네트워크 서비스: ").append(network).append("건\n");
        b.append("누적 저장 항목: ").append(total).append("건\n");
        b.append("BLE와 BT가 같은 주소인 후보: ").append(sharedMacCandidates).append("건\n");
        b.append("UPnP 확장 요청 탐색: ").append(extendedLan ? "사용자가 활성화" : "비활성").append("\n\n");
        b.append("[프로토콜별 실제 스캔 상태]\n");
        String[] radios = {"BLE", "BT", "WIFI", "LAN", "P2P", "NSD", "UPNP"};
        for (String protocol : radios) {
            String state = radioStates == null ? null : radioStates.get(protocol);
            b.append(protocol).append(": ").append(state == null ? "미실행/상태 미확인" : state)
                    .append('\n');
        }
        b.append("\n[해석 및 한계]\n");
        b.append("1. 위 수치는 '실제 전자기기 수'가 아니라 무선 주소 및 네트워크 서비스 항목 수입니다.\n");
        b.append("2. 서로 다른 주소/서비스가 하나의 기기일 수 있습니다. 동일 BLE·BT 주소도 확정 식별은 아닙니다.\n");
        b.append("3. BLE 주소 임의화, 비광고, Wi-Fi 검색 제한, 숨김 SSID와 응답 부재로 누락될 수 있습니다.\n");
        b.append("4. 네트워크 서비스(mDNS/UPnP)와 LAN TCP 응답 IP는 유선 장치일 수도 있습니다.\n");
        b.append("4-1. LAN TCP 확인은 연결된 사설 Wi-Fi 네트워크에 사용자가 허용한 경우만 시도하고, 소수 포트에 연결만 확인합니다. 응답이 없다는 것은 부재 증거가 아닙니다.\n");
        b.append("5. SDR/전용 수신기 없이는 Zigbee·LoRa·433MHz 등 임의 주파수를 탐색할 수 없습니다.\n");
        b.append("6. RSSI는 주변 전파 환경에 따라 달라지므로 절대 거리·방향·위치를 보장하지 않습니다.\n");
        b.append("7. 이 보고서는 수집 당시 Android API로 관찰 가능한 증거만 기록합니다.\n\n");
        b.append("[실기기 확인 체크리스트]\n");
        b.append("[ ] Bluetooth / Wi-Fi / 위치 서비스 사용 여부를 확인했다\n");
        b.append("[ ] 권한 거부·무선 스캔 실패 메시지를 검토했다\n");
        b.append("[ ] 알려진 BLE 기기와 Wi-Fi AP를 여러 차례 반복 탐색했다\n");
        b.append("[ ] 이동 장소/시간을 바꿔 미발견 기기를 다시 탐색했다\n");
        b.append("[ ] 필수 업무망·민감 네트워크에선 승인 없이 확장 요청 탐색을 사용하지 않았다\n");
        return b.toString();
    }
}
