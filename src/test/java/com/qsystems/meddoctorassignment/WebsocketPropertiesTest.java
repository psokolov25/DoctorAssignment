package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.config.WebsocketProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebsocketPropertiesTest {

    @Test
    void websocketCookiesAreDisabledByDefault() {
        WebsocketProperties properties = new WebsocketProperties();

        Assertions.assertFalse(properties.isSendCookiesInHandshake());
        Assertions.assertTrue(properties.isEnabled());
    }
}
