package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.application.imports.ImportIssue.Severity;
import org.springframework.context.MessageSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Accumulates every finding of one import (never fail-fast: the user fixes the whole file in one
 * pass). Messages are resolved immediately in the requester's locale. Storage is capped so a
 * pathological file can't produce an unbounded report; counters keep counting past the cap.
 */
public final class IssueCollector {

    static final int MAX_STORED_ISSUES = 500;

    private final MessageSource messageSource;
    private final Locale locale;
    private final List<ImportIssue> issues = new ArrayList<>();
    private final Set<Integer> rowsWithErrors = new HashSet<>();
    private int errorCount;
    private int warningCount;

    public IssueCollector(MessageSource messageSource, Locale locale) {
        this.messageSource = messageSource;
        this.locale = locale;
    }

    public void error(Integer row, String column, String messageKey, Object... args) {
        errorCount++;
        if (row != null) {
            rowsWithErrors.add(row);
        }
        store(new ImportIssue(row, column, Severity.ERROR, messageKey, resolve(messageKey, args)));
    }

    public void warning(Integer row, String column, String messageKey, Object... args) {
        warningCount++;
        store(new ImportIssue(row, column, Severity.WARNING, messageKey, resolve(messageKey, args)));
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    public boolean rowHasErrors(int row) {
        return rowsWithErrors.contains(row);
    }

    public int rowsWithErrorsCount() {
        return rowsWithErrors.size();
    }

    public int errorCount() {
        return errorCount;
    }

    public int warningCount() {
        return warningCount;
    }

    public boolean truncated() {
        return errorCount + warningCount > issues.size();
    }

    public List<ImportIssue> issues() {
        return List.copyOf(issues);
    }

    public String resolve(String messageKey, Object... args) {
        return messageSource.getMessage(messageKey, args, messageKey, locale);
    }

    private void store(ImportIssue issue) {
        if (issues.size() < MAX_STORED_ISSUES) {
            issues.add(issue);
        }
    }
}
