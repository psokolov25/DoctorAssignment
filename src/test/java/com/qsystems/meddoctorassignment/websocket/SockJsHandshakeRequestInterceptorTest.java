package com.qsystems.meddoctorassignment.websocket;

import com.qsystems.meddoctorassignment.adapter.orchestra.OrchestraSessionCookieStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SockJsHandshakeRequestInterceptorTest {

    @Test
    void addsAuthorizationAndReadCookiesToSockJsHttpRequests() throws Exception {
        OrchestraSessionCookieStore cookieStore = new OrchestraSessionCookieStore();
        cookieStore.captureResponseCookies("GET", java.util.Collections.singletonList("SSOcookie=test-read-session; Path=/; HttpOnly"));

        SockJsHandshakeRequestInterceptor interceptor =
                new SockJsHandshakeRequestInterceptor("doctor", "secret", cookieStore, false);

        TestHttpRequest request = new TestHttpRequest(URI.create("http://localhost/qpevents/events/info"));
        RecordingExecution execution = new RecordingExecution();
        interceptor.intercept(request, new byte[0], execution);

        String expectedAuthorization = "Basic " + Base64.getEncoder()
                .encodeToString("doctor:secret".getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedAuthorization, request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(null, request.getHeaders().getFirst(HttpHeaders.COOKIE));
        assertTrue(execution.executed);
    }

    @Test
    void keepsExistingHeadersUntouched() throws Exception {
        OrchestraSessionCookieStore cookieStore = new OrchestraSessionCookieStore();
        SockJsHandshakeRequestInterceptor interceptor =
                new SockJsHandshakeRequestInterceptor("doctor", "secret", cookieStore, false);

        TestHttpRequest request = new TestHttpRequest(URI.create("http://localhost/qpevents/events/info"));
        request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer custom");
        request.getHeaders().add(HttpHeaders.COOKIE, "SSOcookie=custom");
        RecordingExecution execution = new RecordingExecution();
        interceptor.intercept(request, new byte[0], execution);

        assertEquals("Bearer custom", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("SSOcookie=custom", request.getHeaders().getFirst(HttpHeaders.COOKIE));
        assertTrue(execution.executed);
    }


    @Test
    void canOptionallyAddReadCookiesToSockJsHttpRequests() throws Exception {
        OrchestraSessionCookieStore cookieStore = new OrchestraSessionCookieStore();
        cookieStore.captureResponseCookies("GET", java.util.Collections.singletonList("SSOcookie=test-read-session; Path=/; HttpOnly"));

        SockJsHandshakeRequestInterceptor interceptor =
                new SockJsHandshakeRequestInterceptor("doctor", "secret", cookieStore, true);

        TestHttpRequest request = new TestHttpRequest(URI.create("http://localhost/qpevents/events/info"));
        RecordingExecution execution = new RecordingExecution();
        interceptor.intercept(request, new byte[0], execution);

        assertEquals("SSOcookie=test-read-session", request.getHeaders().getFirst(HttpHeaders.COOKIE));
        assertTrue(execution.executed);
    }

    private static final class TestHttpRequest implements HttpRequest {
        private final URI uri;
        private final HttpHeaders headers = new HttpHeaders();

        private TestHttpRequest(URI uri) {
            this.uri = uri;
        }

        @Override
        public HttpMethod getMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String getMethodValue() {
            return HttpMethod.GET.name();
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

    private static final class RecordingExecution implements ClientHttpRequestExecution {
        private boolean executed;

        @Override
        public ClientHttpResponse execute(HttpRequest request, byte[] body) {
            this.executed = true;
            return new EmptyResponse();
        }
    }

    private static final class EmptyResponse implements ClientHttpResponse {
        @Override
        public HttpStatus getStatusCode() {
            return HttpStatus.OK;
        }

        @Override
        public int getRawStatusCode() {
            return 200;
        }

        @Override
        public String getStatusText() {
            return "OK";
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }
    }
}
