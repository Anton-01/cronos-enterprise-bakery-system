package com.ninsky.cronos.kitchen.guide;

import java.util.List;

/** {@code GET /baking-guide}: everything the guide page needs in one call; {@code revision} is an ISO date. */
public record BakingGuide(List<GuideArticle> articles, List<PanSize> panSizes, List<IngredientConversion> conversions, String revision) {
}
