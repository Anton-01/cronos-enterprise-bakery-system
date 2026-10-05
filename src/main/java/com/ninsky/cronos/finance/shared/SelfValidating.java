package com.ninsky.cronos.finance.shared;

import java.util.List;

/** A request whose cross-field domain rules are reported next to its Bean Validation errors (N2). */
public interface SelfValidating {

    /** Rule failures; message keys must not need positional arguments. */
    List<FieldIssue> ruleIssues();
}
