package com.alvinliu.dbmcp.audit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Audit logging for SQL operations. Same format and rotation as Go Auditor:
 * 10MB per file, filename base_yyyy-MM-dd_HHmmss.log; reuse most recent file under 10MB or create new.
 */
public class Auditor {
    private static final long MAX_SIZE = 10L << 20; // 10MB
    private static final DateTimeFormatter ROTATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(ZoneId.systemDefault());

    private File file;
    private final Object lock = new Object();
    private long currentSize;
    private final String dir;
    private final String base;
    private final String ext;

    public Auditor(String logFile) throws IOException {
        Path p = Paths.get(logFile).normalize();
        Path parent = p.getParent();
        dir = parent != null ? parent.toString() : ".";
        String filename = p.getFileName() != null ? p.getFileName().toString() : "audit.log";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            base = filename.substring(0, dot);
            ext = filename.substring(dot);
        } else {
            base = filename.isEmpty() ? "audit" : filename;
            ext = ".log";
        }
        openOrCreate();
    }

    private void openOrCreate() throws IOException {
        String pattern = base + "_*" + ext;
        Path dirPath = Paths.get(dir);
        if (!Files.isDirectory(dirPath)) {
            Files.createDirectories(dirPath);
        }
        List<Path> matches = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, pattern)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) matches.add(entry);
            }
        }
        matches.sort(Comparator.comparing(Path::toString).reversed());
        for (Path path : matches) {
            long size = Files.size(path);
            if (size < MAX_SIZE) {
                file = path.toFile();
                currentSize = size;
                return;
            }
        }
        rotateOpen();
    }

    private void rotateOpen() throws IOException {
        if (file != null) {
            try { new FileOutputStream(file, true).getChannel().force(true); } catch (IOException ignored) {}
            try { new FileWriter(file, true).close(); } catch (IOException ignored) {}
            file = null;
        }
        String name = base + "_" + ROTATE_FORMAT.format(Instant.now()) + ext;
        Path path = Paths.get(dir, name);
        file = path.toFile();
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
        currentSize = file.length();
    }

    /**
     * Write one audit entry. Rotates to a new file when current file would exceed 10MB.
     */
    public void log(String sql, List<String> matchedKeywords, boolean approved, String action,
                    String connection, String databaseName, String schema, String driver) {
        log(sql, matchedKeywords, approved, action, connection, databaseName, schema, driver, null);
    }

    /**
     * Write one audit entry, optionally with output file path (e.g. for query_to_csv_file / query_to_text_file).
     */
    public void log(String sql, List<String> matchedKeywords, boolean approved, String action,
                    String connection, String databaseName, String schema, String driver, String outputFile) {
        String keywords = (matchedKeywords != null && !matchedKeywords.isEmpty())
            ? String.join(",", matchedKeywords) : "none";
        if (connection == null || connection.isEmpty()) connection = "default";
        if (databaseName == null || databaseName.isEmpty()) databaseName = "-";
        if (schema == null || schema.isEmpty()) schema = "-";
        if (driver == null || driver.isEmpty()) driver = "-";

        String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault()).format(Instant.now());
        StringBuilder header = new StringBuilder();
        header.append("AUDIT_TIME=").append(timestamp).append("\n")
            .append("AUDIT_CONNECTION=").append(connection).append("\n")
            .append("AUDIT_DRIVER=").append(driver).append("\n")
            .append("AUDIT_KEYWORDS=").append(keywords).append("\n")
            .append("AUDIT_APPROVED=").append(approved).append("\n")
            .append("AUDIT_ACTION=").append(action).append("\n");
        if (outputFile != null && !outputFile.isEmpty()) {
            header.append("AUDIT_OUTPUT_FILE=").append(outputFile).append("\n");
        }
        header.append("AUDIT_SQL=\n");
        String entry = header.toString() + sql + (sql.endsWith("\n") ? "" : "\n") + "######AUDIT_END######\n";
        long size = entry.getBytes(StandardCharsets.UTF_8).length;

        synchronized (lock) {
            if (currentSize + size >= MAX_SIZE && currentSize > 0) {
                try {
                    rotateOpen();
                } catch (IOException e) {
                    // write to current file anyway
                }
            }
            try (FileOutputStream fos = new FileOutputStream(file, true);
                 OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                w.write(entry);
                w.flush();
                fos.getChannel().force(true);
            } catch (IOException ignored) {
                // best effort
            }
            currentSize += size;
        }
    }

    public void close() throws IOException {
        synchronized (lock) {
            if (file != null) {
                // no hold on file handle; nothing to close per entry
                file = null;
            }
        }
    }
}
