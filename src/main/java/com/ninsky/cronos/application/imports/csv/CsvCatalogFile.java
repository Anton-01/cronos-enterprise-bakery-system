package com.ninsky.cronos.application.imports.csv;

import com.ninsky.cronos.domain.service.core.MeasurementUnitRules;
import com.ninsky.cronos.infrastructure.exception.CsvImportRejectedException;
import com.ninsky.cronos.infrastructure.exception.CsvImportRejectedException.RowError;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Parses and validates a whole catalog CSV before anything is written (all-or-nothing): required
 * headers, strict UTF-8, row limit, and typed per-cell reads that collect every problem with its CSV
 * line instead of failing on the first one. Call {@link #rejectIfInvalid()} after reading every row.
 */
public final class CsvCatalogFile {

    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final List<CSVRecord> records;
    private final List<RowError> errors = new ArrayList<>();

    private CsvCatalogFile(List<CSVRecord> records) {
        this.records = records;
    }

    /**
     * @throws CsvImportRejectedException when the file is empty, not UTF-8, malformed, misses a
     *                                    required header or exceeds {@code maxRows}
     */
    public static CsvCatalogFile parse(MultipartFile file, Set<String> requiredHeaders, int maxRows) {
        try (InputStream content = file.getInputStream()) {
            return parse(content, requiredHeaders, maxRows);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded CSV", e);
        }
    }

    static CsvCatalogFile parse(InputStream content, Set<String> requiredHeaders, int maxRows) {
        CSVFormat format = CSVFormat.Builder.create()
                .setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true).setIgnoreEmptyLines(true)
                .setIgnoreHeaderCase(true).setTrim(true)
                .get();
        try (CSVParser parser = format.parse(utf8Reader(content))) {
            Set<String> headers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            headers.addAll(parser.getHeaderNames());
            List<RowError> headerErrors = requiredHeaders.stream()
                    .filter(header -> !headers.contains(header))
                    .map(header -> RowError.of(1, header, "import.header.missing", header))
                    .toList();
            if (!headerErrors.isEmpty()) {
                throw new CsvImportRejectedException(headerErrors);
            }
            List<CSVRecord> records = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (records.size() == maxRows) {
                    throw new CsvImportRejectedException(List.of(RowError.of(null, null, "import.csv.tooManyRows", maxRows)));
                }
                records.add(record);
            }
            if (records.isEmpty()) {
                throw new CsvImportRejectedException(List.of(RowError.of(null, null, "import.csv.noRows")));
            }
            return new CsvCatalogFile(records);
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            // Commons CSV: IllegalArgument for an empty or duplicated header, UncheckedIO/IllegalState for a
            // malformed line; the strict decoder's CharacterCodingException may arrive wrapped in UncheckedIO.
            boolean notUtf8 = e instanceof CharacterCodingException || e.getCause() instanceof CharacterCodingException;
            throw new CsvImportRejectedException(List.of(RowError.of(null, null, notUtf8 ? "import.csv.notUtf8" : "import.csv.unreadable")));
        }
    }

    /**
     * Strict UTF-8 (a Latin-1/ANSI export would otherwise be silently mangled: "Ã±" instead of "ñ"),
     * skipping the byte-order mark Excel's "CSV UTF-8" export prepends — left in, it would become
     * part of the first header name.
     */
    private static Reader utf8Reader(InputStream content) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(content);
        buffered.mark(UTF8_BOM.length);
        byte[] head = buffered.readNBytes(UTF8_BOM.length);
        if (!Arrays.equals(head, UTF8_BOM)) {
            buffered.reset();
        }
        return new InputStreamReader(buffered, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
    }

    public List<CSVRecord> records() {
        return records;
    }

    /** 1-based line of the file as an editor shows it (header = 1). */
    public static int lineOf(CSVRecord record) {
        return (int) record.getRecordNumber() + 1;
    }

    /** Trimmed, NFC-normalized, control characters removed; empty when blank or the column is absent. */
    public Optional<String> text(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return Optional.empty();
        }
        String clean = CONTROL_CHARS.matcher(Normalizer.normalize(record.get(column), Normalizer.Form.NFC)).replaceAll("").trim();
        return clean.isEmpty() ? Optional.empty() : Optional.of(clean);
    }

    /** Required free text: present, at most {@code maxLength}, not a spreadsheet formula trigger. */
    public Optional<String> requiredText(CSVRecord record, String column, int maxLength) {
        return checkedText(record, column, maxLength, true);
    }

    public Optional<String> optionalText(CSVRecord record, String column, int maxLength) {
        return checkedText(record, column, maxLength, false);
    }

    public <E extends Enum<E>> Optional<E> requiredEnum(CSVRecord record, String column, Class<E> type) {
        Optional<String> value = text(record, column);
        if (value.isEmpty()) {
            error(record, column, "import.cell.required", column);
            return Optional.empty();
        }
        String normalized = value.get().toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalized)) {
                return Optional.of(constant);
            }
        }
        error(record, column, "import.cell.notAllowed", value.get(), Arrays.toString(type.getEnumConstants()));
        return Optional.empty();
    }

    /**
     * Records an error and returns true when {@code key} (the row's natural key, normalized by the
     * caller) already appeared on an earlier line; {@code value} is what the user typed, for the message.
     */
    public boolean isDuplicate(CSVRecord record, String column, String value, String key, Map<String, Integer> firstLineOfKey) {
        Integer firstLine = firstLineOfKey.putIfAbsent(key, lineOf(record));
        if (firstLine != null) {
            error(record, column, "import.csv.duplicateValue", value, firstLine);
            return true;
        }
        return false;
    }

    public void error(CSVRecord record, String column, String messageKey, Object... args) {
        errors.add(RowError.of(lineOf(record), column, messageKey, args));
    }

    public boolean hasErrors(CSVRecord record) {
        int line = lineOf(record);
        return errors.stream().anyMatch(e -> e.line() != null && e.line() == line);
    }

    /** All-or-nothing gate: throws with every collected error, ordered by line. */
    public void rejectIfInvalid() {
        if (!errors.isEmpty()) {
            Map<Integer, List<RowError>> byLine = new TreeMap<>();
            errors.forEach(e -> byLine.computeIfAbsent(e.line(), l -> new ArrayList<>()).add(e));
            throw new CsvImportRejectedException(byLine.values().stream().flatMap(List::stream).toList());
        }
    }

    private Optional<String> checkedText(CSVRecord record, String column, int maxLength, boolean required) {
        Optional<String> value = text(record, column);
        if (value.isEmpty()) {
            if (required) {
                error(record, column, "import.cell.required", column);
            }
            return Optional.empty();
        }
        if (value.get().length() > maxLength) {
            error(record, column, "import.cell.tooLong", column, maxLength);
            return Optional.empty();
        }
        if (MeasurementUnitRules.checkDisplayText(value.get()).isPresent()) {
            error(record, column, "catalog.text.formulaInjection", column);
            return Optional.empty();
        }
        return value;
    }
}
