package com.ninsky.cronos.iam.access;

/** Where an effective permission comes from. */
public enum SourceType {
    ROLE,
    ROLE_GROUP,
    USER_GROUP,
    DIRECT_GRANT
}
