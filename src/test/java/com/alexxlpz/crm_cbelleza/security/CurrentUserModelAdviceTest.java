package com.alexxlpz.crm_cbelleza.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserModelAdviceTest {

    @Test
    void mapsCurrentPathToAccountSection() {
        assertThat(CurrentUserModelAdvice.accountSection("/worker/dashboard")).isEqualTo("panel");
        assertThat(CurrentUserModelAdvice.accountSection("/worker/calendar")).isEqualTo("panel");
        assertThat(CurrentUserModelAdvice.accountSection("/worker/clients/3")).isEqualTo("panel");
        assertThat(CurrentUserModelAdvice.accountSection("/worker/center")).isEqualTo("center");
        assertThat(CurrentUserModelAdvice.accountSection("/client/appointments")).isEqualTo("appointments");
        assertThat(CurrentUserModelAdvice.accountSection("/client/profile")).isEqualTo("profile");
        assertThat(CurrentUserModelAdvice.accountSection("/centers")).isNull();
        assertThat(CurrentUserModelAdvice.accountSection(null)).isNull();
    }
}
