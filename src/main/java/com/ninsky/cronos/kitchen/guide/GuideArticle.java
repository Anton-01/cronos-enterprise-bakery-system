package com.ninsky.cronos.kitchen.guide;

import java.util.List;
import java.util.UUID;

/** A guide article as served to readers, already localised (§6.1). */
public record GuideArticle(UUID id, String code, GuideCategory category, String title, String summary, String icon, List<String> tags,
                           List<GuideBlock> blocks, List<String> sources, int displayOrder) {
}
