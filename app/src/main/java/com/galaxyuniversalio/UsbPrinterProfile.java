package com.galaxyuniversalio;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Strict, data-only printer profile. It can select built-in transports/renderers, never load code. */
public final class UsbPrinterProfile {
    private static final String PROFILE_URL = "https://raw.githubusercontent.com/sungok2/sungok/galaxy-universal-io-m1-escl-discovery/printer-profiles/WF-C5390.profile";
    private static final int MAX_PROFILE_BYTES = 4096;

    public final String manufacturer;
    public final String model;
    public final String pdl;
    public final int dpi;
    public final int maxPages;
    public final long maxJobBytes;
    public final String source;

    private UsbPrinterProfile(String manufacturer, String model, String pdl, int dpi,
                              int maxPages, long maxJobBytes, String source) {
        this.manufacturer = manufacturer;
        this.model = model;
        this.pdl = pdl;
        this.dpi = dpi;
        this.maxPages = maxPages;
        this.maxJobBytes = maxJobBytes;
        this.source = source;
    }

    public static UsbPrinterProfile fetchWfC5390(Context context) throws IOException {
        HttpURLConnection connection = null;
        byte[] remote;
        try {
            connection = (HttpURLConnection) new URL(PROFILE_URL).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "text/plain");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub 프로파일 응답 코드 " + code);
            }
            try (InputStream input = connection.getInputStream()) {
                remote = readBounded(input);
            }
        } catch (IOException networkFailure) {
            // Keep the supported model usable offline, but disclose the fallback in the UI.
            try (InputStream asset = context.getAssets().open("profiles/WF-C5390.profile")) {
                UsbPrinterProfile local = parse(readBounded(asset), "앱 내장 오프라인 프로파일");
                return new UsbPrinterProfile(local.manufacturer, local.model, local.pdl,
                        local.dpi, local.maxPages, local.maxJobBytes,
                        "오프라인 대체 (온라인 확인 실패: " + networkFailure.getMessage() + ")");
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
        // Invalid online data must fail closed instead of silently using the offline copy.
        return parse(remote, "GitHub");
    }

    static UsbPrinterProfile parse(byte[] bytes, String source) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_PROFILE_BYTES) {
            throw new IOException("프로파일 크기가 허용 범위를 벗어났습니다.");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        String manufacturer = null, model = null, pdl = null;
        int dpi = -1, maxPages = -1;
        long maxJobBytes = -1;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int sep = line.indexOf('=');
            if (sep <= 0) throw new IOException("프로파일 문법 오류");
            String key = line.substring(0, sep).trim();
            String value = line.substring(sep + 1).trim();
            try {
                switch (key) {
                    case "manufacturer": manufacturer = value; break;
                    case "model": model = value; break;
                    case "pdl": pdl = value; break;
                    case "dpi": dpi = Integer.parseInt(value); break;
                    case "maxPages": maxPages = Integer.parseInt(value); break;
                    case "maxJobBytes": maxJobBytes = Long.parseLong(value); break;
                    default: throw new IOException("허용되지 않은 프로파일 항목: " + key);
                }
            } catch (NumberFormatException e) {
                throw new IOException("프로파일 숫자 값 오류: " + key, e);
            }
        }
        // Allowlisted values prevent downloaded data from activating arbitrary code or commands.
        if (!"EPSON".equals(manufacturer) || !"WF-C5390".equals(model)
                || !"POSTSCRIPT3".equals(pdl) || dpi != 150
                || maxPages < 1 || maxPages > 20
                || maxJobBytes < 1024 * 1024 || maxJobBytes > 64L * 1024 * 1024) {
            throw new IOException("WF-C5390 프로파일이 허용 범위와 일치하지 않습니다.");
        }
        return new UsbPrinterProfile(manufacturer, model, pdl, dpi, maxPages, maxJobBytes, source);
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (out.size() > MAX_PROFILE_BYTES - count) throw new IOException("프로파일이 너무 큽니다.");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}
