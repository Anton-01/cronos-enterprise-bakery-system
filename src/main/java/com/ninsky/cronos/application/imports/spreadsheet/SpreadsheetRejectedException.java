package com.ninsky.cronos.application.imports.spreadsheet;

import java.util.List;

/** The file itself is unusable (not an .xlsx, encrypted, macro-enabled, sheet missing, too large…). */
public class SpreadsheetRejectedException extends RuntimeException {

    private final String messageKey;
    private final transient List<Object> args;

    public SpreadsheetRejectedException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = List.of(args);
    }

    public SpreadsheetRejectedException(String messageKey, Throwable cause, Object... args) {
        super(messageKey, cause);
        this.messageKey = messageKey;
        this.args = List.of(args);
    }

    public String messageKey() {
        return messageKey;
    }

    public Object[] args() {
        return args.toArray();
    }
}
