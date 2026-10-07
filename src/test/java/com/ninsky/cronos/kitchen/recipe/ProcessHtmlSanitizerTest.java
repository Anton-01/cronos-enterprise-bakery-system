package com.ninsky.cronos.kitchen.recipe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessHtmlSanitizerTest {

    @Test
    void keepsAllowedFormatting() {
        String html = "<ol><li><strong>Mezclar</strong> la harina</li><li><em>Hornear</em></li></ol>";

        assertThat(ProcessHtmlSanitizer.sanitize(html)).isEqualTo(html);
    }

    @Test
    void stripsScriptsAndStyles() {
        String clean = ProcessHtmlSanitizer.sanitize("<p>Batir<script>alert(1)</script><style>p{color:red}</style></p>");

        assertThat(clean).contains("Batir").doesNotContain("script", "alert", "style", "color");
    }

    @Test
    void stripsEventHandlersAndDisallowedTags() {
        String clean = ProcessHtmlSanitizer.sanitize("<p onclick=\"steal()\">Reposar<img src=x onerror=alert(1)></p>");

        assertThat(clean).isEqualTo("<p>Reposar</p>");
    }

    @Test
    void dropsJavascriptLinksButKeepsSafeOnes() {
        assertThat(ProcessHtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">ver</a>")).doesNotContain("javascript", "href");
        assertThat(ProcessHtmlSanitizer.sanitize("<a href=\"https://cronos.mx\" target=\"_blank\">ver</a>"))
                .contains("href=\"https://cronos.mx\"", "noopener", "noreferrer");
    }

    @Test
    void keepsOnlyEditorClasses() {
        assertThat(ProcessHtmlSanitizer.sanitize("<span class=\"ql-size-large\">Nota</span>")).contains("ql-size-large");
        assertThat(ProcessHtmlSanitizer.sanitize("<span class=\"evil\">Nota</span>")).doesNotContain("evil").contains("Nota");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "<p></p>", "<p>&nbsp;</p>", "<p><br></p>", "<script>alert(1)</script>", "<img src=x onerror=alert(1)>"})
    void returnsNullWhenNoTextRemains(String html) {
        assertThat(ProcessHtmlSanitizer.sanitize(html)).isNull();
    }
}
