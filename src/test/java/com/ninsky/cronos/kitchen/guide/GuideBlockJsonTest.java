package com.ninsky.cronos.kitchen.guide;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The wire shape must match {@code GuideBlock} in {@code baking-guide.models.ts}. */
class GuideBlockJsonTest {

    private static final TypeReference<List<GuideBlock>> BLOCKS = new TypeReference<>() {
    };

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsEveryBlockTypeOfTheSeedShape() throws Exception {
        List<GuideBlock> blocks = mapper.readValue("""
                [{"type":"paragraph","text":"p"},{"type":"list","items":["a"]},{"type":"table","columns":["c"],"rows":[["1"]]},
                 {"type":"callout","tone":"tip","text":"t"},{"type":"formula","expression":"e","description":"d"}]""", BLOCKS);

        assertThat(blocks).hasExactlyElementsOfTypes(GuideBlock.Paragraph.class, GuideBlock.ListBlock.class, GuideBlock.Table.class,
                GuideBlock.Callout.class, GuideBlock.Formula.class);
        assertThat(((GuideBlock.ListBlock) blocks.get(1)).ordered()).isFalse();
    }

    @Test
    void writesTheDiscriminatorWithTheDeclaredType() throws Exception {
        String json = mapper.writerFor(BLOCKS).writeValueAsString(List.of(new GuideBlock.Callout("warn", "x")));

        assertThat(json).isEqualTo("[{\"type\":\"callout\",\"tone\":\"warn\",\"text\":\"x\"}]");
    }

    @Test
    void translationsKeepTheDiscriminatorOfNestedBlocks() throws Exception {
        Map<String, GuideAdmin.ArticleTranslation> translations = Map.of("en",
                new GuideAdmin.ArticleTranslation("T", null, null, List.of(new GuideBlock.Paragraph("Hi"))));

        assertThat(mapper.writeValueAsString(translations)).contains("\"type\":\"paragraph\"");
    }

    @Test
    void rejectsUnknownBlockTypes() {
        assertThatThrownBy(() -> mapper.readValue("[{\"type\":\"html\",\"text\":\"<b>x</b>\"}]", BLOCKS))
                .isInstanceOf(InvalidTypeIdException.class);
    }
}
