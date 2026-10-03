package com.ninsky.cronos.application.imports;

/** An uploaded file, detached from the web layer. {@code contentType} is client-declared and untrusted. */
public record ImportFile(String fileName, String contentType, byte[] content) {

    public ImportFile {
        fileName = fileName == null || fileName.isBlank() ? "unnamed" : fileName.strip();
        content = content == null ? new byte[0] : content;
    }

    public long size() {
        return content.length;
    }
}
