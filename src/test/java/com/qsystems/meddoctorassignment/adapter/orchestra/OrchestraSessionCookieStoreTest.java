package com.qsystems.meddoctorassignment.adapter.orchestra;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

public class OrchestraSessionCookieStoreTest {

    @Test
    void keepsGetCookiesOnlyForGetRequests() {
        OrchestraSessionCookieStore store = new OrchestraSessionCookieStore();
        store.captureResponseCookies("GET", Arrays.asList(
                "SSOcookie=get-cookie; Path=/; HttpOnly",
                "rememberMe=deleteMe; Path=/; Max-Age=0"
        ));

        Assertions.assertEquals("SSOcookie=get-cookie", store.buildCookieHeaderForMethod("GET"));
        Assertions.assertEquals("", store.buildCookieHeaderForMethod("PUT"));
        Assertions.assertEquals("SSOcookie=get-cookie", store.buildCookieHeaderForWebsocketHandshake());
    }

    @Test
    void doesNotReplayMutatingCookiesByDefault() {
        OrchestraSessionCookieStore store = new OrchestraSessionCookieStore();
        store.captureResponseCookies("PUT", Collections.singletonList(
                "SSOcookie=put-cookie; Path=/; HttpOnly"
        ));

        Assertions.assertEquals("", store.buildCookieHeaderForMethod("PUT"));
        Assertions.assertEquals("", store.buildCookieHeaderForMethod("POST"));
        Assertions.assertEquals("", store.buildCookieHeaderForMethod("GET"));
    }

    @Test
    void canExplicitlyEnableMutatingCookieReplay() {
        OrchestraSessionCookieStore store = new OrchestraSessionCookieStore();
        store.setReplayMutationCookies(true);
        store.captureResponseCookies("PUT", Collections.singletonList(
                "SSOcookie=put-cookie; Path=/; HttpOnly"
        ));

        Assertions.assertEquals("SSOcookie=put-cookie", store.buildCookieHeaderForMethod("PUT"));
        Assertions.assertEquals("SSOcookie=put-cookie", store.buildCookieHeaderForMethod("POST"));
        Assertions.assertEquals("", store.buildCookieHeaderForMethod("GET"));
    }

    @Test
    void removesCookieWhenServerReturnsDeletionMarker() {
        OrchestraSessionCookieStore store = new OrchestraSessionCookieStore();
        store.captureResponseCookies("GET", Collections.singletonList(
                "SSOcookie=alive; Path=/; HttpOnly"
        ));
        store.captureResponseCookies("GET", Collections.singletonList(
                "SSOcookie=deleteMe; Path=/; Max-Age=0"
        ));

        Assertions.assertEquals("", store.buildCookieHeaderForMethod("GET"));
    }
}
