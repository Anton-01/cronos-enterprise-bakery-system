package com.ninsky.cronos.iam.shared;

import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {

    @Test
    void masksContactData() {
        assertThat(Masking.email("carla@cronos.mx")).isEqualTo("c***@cronos.mx");
        assertThat(Masking.phone("+525511223344")).isEqualTo("***3344");
    }
}
