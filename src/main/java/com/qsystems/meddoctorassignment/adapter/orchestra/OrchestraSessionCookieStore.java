package com.qsystems.meddoctorassignment.adapter.orchestra;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Хранилище cookie Orchestra с раздельными областями видимости по типу HTTP-операции.
 *
 * <p>GET-cookie используются только для GET-подобных запросов и SockJS/XHR handhsake, а
 * mutating-cookie (PUT/POST/PATCH/DELETE) используются только для mutating-запросов.</p>
 */
@Singleton
public class OrchestraSessionCookieStore {

    static final String READ_SCOPE = "READ";
    static final String MUTATION_SCOPE = "MUTATION";
    private static final Logger log = LoggerFactory.getLogger(OrchestraSessionCookieStore.class);
    private final Map<String, Map<String, String>> cookiesByScope = new ConcurrentHashMap<String, Map<String, String>>();
    private volatile boolean replayMutationCookies = false;

    /**
     * Возвращает scope для HTTP-метода.
     */
    public String resolveScope(String methodName) {
        if (methodName == null) {
            return MUTATION_SCOPE;
        }
        if ("GET".equalsIgnoreCase(methodName.trim())) {
            return READ_SCOPE;
        }
        return MUTATION_SCOPE;
    }

    /**
     * Управляет replay mutating cookie между PUT/POST/PATCH/DELETE.
     *
     * <p>По умолчанию выключено: свежие логи показывают, что первый PUT без cookie
     * проходит, а последующие PUT с серверным mutation-cookie массово получают 403.</p>
     */
    @Value("${application.orchestra.replay-mutation-cookies:false}")
    public void setReplayMutationCookies(boolean replayMutationCookies) {
        this.replayMutationCookies = replayMutationCookies;
    }

    public boolean isCookieReplayEnabledForMethod(String methodName) {
        String scope = resolveScope(methodName);
        return READ_SCOPE.equals(scope) || replayMutationCookies;
    }

    /**
     * Применяет Set-Cookie заголовки к соответствующему scope.
     */
    public void captureResponseCookies(String methodName, List<String> setCookieHeaders) {
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return;
        }

        String scope = resolveScope(methodName);
        Map<String, String> scopeCookies = cookiesByScope.computeIfAbsent(scope,
                key -> new ConcurrentHashMap<String, String>());

        for (String setCookieHeader : setCookieHeaders) {
            applySetCookieHeader(scopeCookies, setCookieHeader);
        }
    }

    /**
     * Возвращает значение Cookie header для указанного метода.
     */
    public String buildCookieHeaderForMethod(String methodName) {
        String scope = resolveScope(methodName);
        if (MUTATION_SCOPE.equals(scope) && !replayMutationCookies) {
            if (log.isDebugEnabled() && cookiesByScope.containsKey(MUTATION_SCOPE)) {
                log.debug("Mutation cookie replay is disabled for method {}. Stored mutation cookies will not be sent.", methodName);
            }
            return "";
        }

        Map<String, String> cookies = cookiesByScope.get(scope);
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }
        return toCookieHeader(cookies);
    }

    /**
     * Возвращает значение Cookie header для websocket/SockJS handshake.
     */
    public String buildCookieHeaderForWebsocketHandshake() {
        return buildCookieHeaderForMethod("GET");
    }

    private void applySetCookieHeader(Map<String, String> scopeCookies, String setCookieHeader) {
        if (setCookieHeader == null || setCookieHeader.trim().isEmpty()) {
            return;
        }

        String[] segments = setCookieHeader.split(";");
        if (segments.length == 0) {
            return;
        }

        String nameValue = segments[0].trim();
        int separatorIndex = nameValue.indexOf('=');
        if (separatorIndex <= 0) {
            return;
        }

        String cookieName = nameValue.substring(0, separatorIndex).trim();
        String cookieValue = nameValue.substring(separatorIndex + 1).trim();
        if (cookieName.isEmpty()) {
            return;
        }

        if (isCookieDeletion(setCookieHeader, cookieValue)) {
            scopeCookies.remove(cookieName);
            return;
        }

        scopeCookies.put(cookieName, cookieValue);
    }

    private boolean isCookieDeletion(String setCookieHeader, String cookieValue) {
        String lowerHeader = setCookieHeader.toLowerCase();
        return lowerHeader.contains("max-age=0")
                || "deleteme".equalsIgnoreCase(cookieValue)
                || lowerHeader.contains("expires=thu, 01 jan 1970")
                || lowerHeader.contains("expires=wed, 31 dec 1969");
    }

    private String toCookieHeader(Map<String, String> cookies) {
        List<String> names = new ArrayList<String>(cookies.keySet());
        Collections.sort(names);

        StringBuilder cookieHeader = new StringBuilder();
        for (String name : names) {
            String value = cookies.get(name);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (cookieHeader.length() > 0) {
                cookieHeader.append("; ");
            }
            cookieHeader.append(name).append('=').append(value);
        }
        return cookieHeader.toString();
    }
}
