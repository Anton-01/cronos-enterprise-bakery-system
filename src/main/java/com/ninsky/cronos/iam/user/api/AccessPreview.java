package com.ninsky.cronos.iam.user.api;

import com.ninsky.cronos.iam.access.EffectiveEntry;
import com.ninsky.cronos.iam.sod.SodConflict;

import java.util.List;

/** Effect of a proposed assignment; nothing persisted. */
public record AccessPreview(List<EffectiveEntry> effective, List<SodConflict> sodConflicts, List<String> added,
                            List<String> removed) {
}
