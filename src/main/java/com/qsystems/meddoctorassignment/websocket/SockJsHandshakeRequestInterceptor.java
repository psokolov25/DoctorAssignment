package com.qsystems.meddoctorassignment.websocket;

import com.qsystems.meddoctorassignment.adapter.orchestra.OrchestraSessionCookieStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Добавляет Basic Auth ко всем HTTP-запросам SockJS/XHR транспорта.
 *
 * <p>Cookie для websocket/SockJS по умолчанию намеренно не используются: REST и websocket
 * разделены по сессионному контуру, поэтому авторизация websocket держится только на заголовке
 * Authorization. При необходимости cookie можно включить отдельным конфиг-флагом.</p>
 */
public class SockJsHandshakeRequestInterceptor implements ClientHttpRequestInterceptor {

    private final String username;
    private final String password;
    private final OrchestraSessionCookieStore cookieStore;
    private final boolean sendCookiesInHandshake;

    public SockJsHandshakeRequestInterceptor(String username,
                                             String password,
                                             OrchestraSessionCookieStore cookieStore,
                                             boolean sendCookiesInHandshake) {
        this.username = username;
        this.password = password;
        this.cookieStore = cookieStore;
        this.sendCookiesInHandshake = sendCookiesInHandshake;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        if (username != null && !username.trim().isEmpty() && !headers.containsKey(HttpHeaders.AUTHORIZATION)) {
            String rawValue = username + ":" + (password != null ? password : "");
            String encodedValue = Base64.getEncoder().encodeToString(rawValue.getBytes(StandardCharsets.UTF_8));
            headers.add(HttpHeaders.AUTHORIZATION, "Basic " + encodedValue);
        }

        if (sendCookiesInHandshake && !headers.containsKey(HttpHeaders.COOKIE)) {
            String cookieHeader = cookieStore.buildCookieHeaderForWebsocketHandshake();
            if (cookieHeader != null && !cookieHeader.trim().isEmpty()) {
                headers.add(HttpHeaders.COOKIE, cookieHeader);
            }
        }

        return execution.execute(request, body);
    }
}
