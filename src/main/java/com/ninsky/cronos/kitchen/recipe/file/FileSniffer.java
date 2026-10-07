package com.ninsky.cronos.kitchen.recipe.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * K6: the type comes from magic bytes, never from {@code Content-Type}. The file name only
 * disambiguates containers (OLE2 → .doc/.xls) and plain text, whose content is still verified.
 */
public final class FileSniffer {

    static final SniffedFile JPEG = new SniffedFile(FileKind.IMAGE, "image/jpeg", "jpg");
    static final SniffedFile PNG = new SniffedFile(FileKind.IMAGE, "image/png", "png");
    static final SniffedFile WEBP = new SniffedFile(FileKind.IMAGE, "image/webp", "webp");
    static final SniffedFile PDF = new SniffedFile(FileKind.PDF, "application/pdf", "pdf");
    static final SniffedFile DOC = new SniffedFile(FileKind.DOCUMENT, "application/msword", "doc");
    static final SniffedFile DOCX = new SniffedFile(FileKind.DOCUMENT,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
    static final SniffedFile TXT = new SniffedFile(FileKind.DOCUMENT, "text/plain", "txt");
    static final SniffedFile XLS = new SniffedFile(FileKind.SPREADSHEET, "application/vnd.ms-excel", "xls");
    static final SniffedFile XLSX = new SniffedFile(FileKind.SPREADSHEET,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
    static final SniffedFile MP4 = new SniffedFile(FileKind.VIDEO, "video/mp4", "mp4");

    private static final int MAX_ZIP_ENTRIES = 5_000;
    private static final int TEXT_SAMPLE = 64 * 1024;

    private FileSniffer() {
    }

    public static Optional<SniffedFile> sniff(byte[] data, String fileName) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        if (starts(data, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (starts(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (starts(data, 0, 'R', 'I', 'F', 'F') && starts(data, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        if (starts(data, 0, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        if (starts(data, 4, 'f', 't', 'y', 'p')) {
            return Optional.of(MP4);
        }
        String extension = extension(fileName);
        if (starts(data, 0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return switch (extension) {
                case "doc" -> Optional.of(DOC);
                case "xls" -> Optional.of(XLS);
                default -> Optional.empty();
            };
        }
        if (starts(data, 0, 'P', 'K', 0x03, 0x04)) {
            return officeOpenXml(data);
        }
        return "txt".equals(extension) && plainText(data) ? Optional.of(TXT) : Optional.empty();
    }

    /** DOCX/XLSX by their part names; any other ZIP is rejected. */
    private static Optional<SniffedFile> officeOpenXml(byte[] data) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            int seen = 0;
            while ((entry = zip.getNextEntry()) != null && seen++ < MAX_ZIP_ENTRIES) {
                String name = entry.getName();
                if (name.startsWith("word/")) {
                    return Optional.of(DOCX);
                }
                if (name.startsWith("xl/")) {
                    return Optional.of(XLSX);
                }
            }
        } catch (IOException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** Strict UTF-8 without NUL bytes in the first 64 KB. */
    private static boolean plainText(byte[] data) {
        byte[] sample = Arrays.copyOf(data, Math.min(data.length, TEXT_SAMPLE));
        for (byte b : sample) {
            if (b == 0) {
                return false;
            }
        }
        int length = sample.length;
        if (data.length > TEXT_SAMPLE) {
            int back = 0;
            while (back < 3 && length - back > 0 && (sample[length - 1 - back] & 0xC0) == 0x80) {
                back++;
            }
            if (length - back > 0 && (sample[length - 1 - back] & 0xC0) == 0xC0) {
                back++;
            }
            length -= back;
        }
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(sample, 0, length));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT).strip();
    }

    private static boolean starts(byte[] data, int offset, int... signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
