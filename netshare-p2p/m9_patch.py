from pathlib import Path
p=Path('android-app/src/main/AndroidManifest.xml')
s=p.read_text()
assert 'CHANGE_WIFI_STATE' not in s
s=s.replace('<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />', '<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />\n    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />\n    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />')
s=s.replace('<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" android:usesPermissionFlags="neverForLocation" />','<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" android:usesPermissionFlags="neverForLocation" />\n    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="32" />')
s=s.replace('P2P Proxy (Conditional M8)','P2P Proxy (Diagnostic M9)')
p.write_text(s)
p=Path('android-app/src/main/java/netshare/app/ProbeActivity.kt')
s=p.read_text()
assert '권한 진단 M9' not in s
s=s.replace('NetShare P2P · 조건부 프록시 M8','NetShare P2P · 권한 진단 M9')
s=s.replace('        lines += "검증된 셀룰러 Network=', '''        lines += "NEARBY_WIFI_DEVICES=${checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED}"
        lines += "CHANGE_WIFI_STATE=${checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED}"
        lines += "CHANGE_NETWORK_STATE=${checkSelfPermission(Manifest.permission.CHANGE_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED}"
        lines += "검증된 셀룰러 Network=''')
s=s.replace('lines.add("활성 P2P IPv4: 없음")', 'lines.add("활성 P2P IPv4: 없음 (P2P 링크가 확인되지 않음)")')
s=s.replace('''        try {
            val channel = manager.initialize(this, mainLooper, null)''','''        var diagnosticStep = "채널 초기화"
        try {
            val channel = manager.initialize(this, mainLooper, null)''')
s=s.replace('''            manager.requestGroupInfo(channel) { group ->''','''            diagnosticStep = "그룹 조회 요청"
            manager.requestGroupInfo(channel) { group ->''')
s=s.replace('''        } catch (e: Exception) { stateText.append("\\nP2P 그룹 조회 실패: ${e.javaClass.simpleName}") }''','''        } catch (e: Exception) {
            val detail = e.message.orEmpty().replace('\\n', ' ').take(300)
            stateText.append("\\n$diagnosticStep 실패: ${e.javaClass.simpleName}: $detail")
        }''')
p.write_text(s)
p=Path('android-app/src/main/java/netshare/app/ProxyService.kt')
s=p.read_text()
s=s.replace('reject("P2P group role query failed: ${e.javaClass.simpleName}", startId)', 'reject("P2P group role query failed: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(180)}", startId)')
p.write_text(s)
p=Path('android-app/build.gradle.kts')
s=p.read_text().replace('versionCode = 8','versionCode = 9').replace('versionName = "0.8.0-conditional"','versionName = "0.9.0-diagnostic"')
p.write_text(s)
print('M9 diagnostic patch applied; no P2P group creation or network-changing calls added')
