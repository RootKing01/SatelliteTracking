package com.satelliteTracking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class SatelliteAuditFileService {

    private static final Logger log = LoggerFactory.getLogger(SatelliteAuditFileService.class);
    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path auditFilePath;

    public SatelliteAuditFileService(@Value("${satellite.audit.file-path:./satellite-new-satellites.txt}") String filePath) {
        this.auditFilePath = Path.of(filePath);
    }

    public synchronized void appendNewSatellite(String source, LocalDateTime timestampUtc, String data) {
        LocalDateTime safeTimestamp = timestampUtc != null ? timestampUtc : LocalDateTime.now(ZoneOffset.UTC);
        String entry = buildEntry(source, safeTimestamp, data);

        try {
            Path parent = auditFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(
                    auditFilePath,
                    entry,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.warn("⚠️ Impossibile scrivere audit file {}: {}", auditFilePath, e.getMessage());
        }
    }

    private String buildEntry(String source, LocalDateTime timestampUtc, String data) {
        StringBuilder sb = new StringBuilder();
        sb.append("[source=").append(source == null ? "unknown" : source)
                .append("] [timestamp=").append(timestampUtc.format(TS_FORMATTER)).append("Z]")
                .append(System.lineSeparator());
        sb.append(data == null ? "" : data.trim()).append(System.lineSeparator());
        sb.append("---").append(System.lineSeparator());
        return sb.toString();
    }
}