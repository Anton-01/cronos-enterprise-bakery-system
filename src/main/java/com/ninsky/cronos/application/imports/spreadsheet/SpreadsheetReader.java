package com.ninsky.cronos.application.imports.spreadsheet;

/**
 * Reads one named worksheet out of an .xlsx file. Implementations must be safe against hostile
 * files (zip bombs, XXE, macro-enabled or legacy/encrypted containers) and reject them with a
 * {@link SpreadsheetRejectedException} rather than any other exception.
 */
public interface SpreadsheetReader {

    /**
     * @param maxDataRows rows after the header beyond which the file is rejected outright (blank
     *                    rows are not counted)
     */
    RawSheet read(byte[] content, String sheetName, int maxDataRows);
}
