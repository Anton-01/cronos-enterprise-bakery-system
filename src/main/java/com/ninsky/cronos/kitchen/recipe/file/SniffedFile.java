package com.ninsky.cronos.kitchen.recipe.file;

/** A file type proven by content: kind, canonical MIME type and extension. */
public record SniffedFile(FileKind kind, String mimeType, String extension) {

    public boolean image() {
        return kind == FileKind.IMAGE;
    }
}
