package com.ninsky.cronos.kitchen.recipe.file;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileSnifferTest {

    private static byte[] bytes(int... values) {
        byte[] data = new byte[Math.max(values.length, 16)];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return data;
    }

    private static byte[] zip(String entry) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void detectsImagesAndPdfByMagicBytesWhateverTheName() {
        assertThat(FileSniffer.sniff(bytes(0xFF, 0xD8, 0xFF, 0xE0), "photo.png")).contains(FileSniffer.JPEG);
        assertThat(FileSniffer.sniff(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A), "photo")).contains(FileSniffer.PNG);
        assertThat(FileSniffer.sniff(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'), "a.webp")).contains(FileSniffer.WEBP);
        assertThat(FileSniffer.sniff("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), "recipe.pdf")).contains(FileSniffer.PDF);
        assertThat(FileSniffer.sniff(bytes(0, 0, 0, 0x18, 'f', 't', 'y', 'p'), "clip.mp4")).contains(FileSniffer.MP4);
    }

    @Test
    void rejectsExecutableRenamedAsImage() {
        assertThat(FileSniffer.sniff(bytes('M', 'Z', 0x90, 0x00), "cake.jpg")).isEmpty();
        assertThat(FileSniffer.sniff(bytes('M', 'Z', 0x90, 0x00), "notes.txt")).isEmpty();
    }

    @Test
    void acceptsUtf8TextOnlyWithTxtExtension() {
        byte[] text = "Receta de concha: harina, azúcar, mantequilla".getBytes(StandardCharsets.UTF_8);

        assertThat(FileSniffer.sniff(text, "concha.TXT")).contains(FileSniffer.TXT);
        assertThat(FileSniffer.sniff(text, "concha.sh")).isEmpty();
        assertThat(FileSniffer.sniff(new byte[]{(byte) 0xC3, 0x28}, "bad.txt")).isEmpty();
    }

    @Test
    void tellsOfficeDocumentsApart() throws IOException {
        assertThat(FileSniffer.sniff(zip("word/document.xml"), "a.docx")).contains(FileSniffer.DOCX);
        assertThat(FileSniffer.sniff(zip("xl/workbook.xml"), "a.docx")).contains(FileSniffer.XLSX);
        assertThat(FileSniffer.sniff(zip("payload.exe"), "a.zip")).isEmpty();

        byte[] ole = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
        assertThat(FileSniffer.sniff(ole, "costs.xls")).contains(FileSniffer.XLS);
        assertThat(FileSniffer.sniff(ole, "recipe.doc")).contains(FileSniffer.DOC);
        assertThat(FileSniffer.sniff(ole, "macro.msi")).isEmpty();
    }

    @Test
    void emptyInputIsRejected() {
        assertThat(FileSniffer.sniff(new byte[0], "a.txt")).isEmpty();
        assertThat(FileSniffer.sniff(null, "a.txt")).isEmpty();
    }
}
