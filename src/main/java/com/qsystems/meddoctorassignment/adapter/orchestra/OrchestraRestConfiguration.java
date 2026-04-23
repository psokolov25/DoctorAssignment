package com.qsystems.meddoctorassignment.adapter.orchestra;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import java.util.List;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * HTTP client filter for Orchestra REST calls.
 *
 * <p>Filter always sends Basic Authorization and keeps cookies isolated by request class:
 * GET cookies are reused only for GET requests, while mutating cookies are reused only for
 * PUT/POST/PATCH/DELETE requests.</p>
 *
 * <p>Before applying its own Cookie header the filter explicitly removes any cookie already
 * attached by the underlying client, so Orchestra session scope is controlled only by this
 * filter and cannot be polluted by a shared client-level cookie jar.</p>
 */
@Filter(value = "${application.orchestra.url}",
        patterns = {"${application.orchestra.configuration-rest-path}/**",
                "${application.orchestra.common-rest-path}/**"})
@Requires(property = "application.orchestra.username")
@Requires(property = "application.orchestra.password")
@Requires(property = "application.orchestra.url")
public class OrchestraRestConfiguration implements HttpClientFilter {

    private static final Logger log = LoggerFactory.getLogger(OrchestraRestConfiguration.class);

    private final String username;
    private final String password;
    private final OrchestraSessionCookieStore cookieStore;

    public OrchestraRestConfiguration(@Value("${application.orchestra.username}") String username,
                                      @Value("${application.orchestra.password}") String password,
                                      OrchestraSessionCookieStore cookieStore) {
        this.username = username;
        this.password = password;
        this.cookieStore = cookieStore;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        applyAuthorization(request);
        applyMethodScopedCookies(request);

        final String methodName = request.getMethod().name();
        return Flux.from(chain.proceed(request))
                .doOnNext(response -> captureResponseCookies(methodName, response));
    }

    private void applyAuthorization(MutableHttpRequest<?> request) {
        if (!request.getHeaders().contains(HttpHeaders.AUTHORIZATION)) {
            request.basicAuth(username, password);
        }
    }

    private void applyMethodScopedCookies(MutableHttpRequest<?> request) {
        if (request.getHeaders().contains(HttpHeaders.COOKIE)) {
            request.getHeaders().remove(HttpHeaders.COOKIE);
            if (log.isDebugEnabled()) {
                log.debug("Removed preexisting Cookie header for {} {} to prevent shared client cookie leakage",
                        request.getMethod(), request.getUri());
            }
        }

        String methodName = request.getMethod().name();
        String cookieHeader = cookieStore.buildCookieHeaderForMethod(methodName);
        if (!cookieHeader.isEmpty()) {
            request.header(HttpHeaders.COOKIE, cookieHeader);
        } else if (!cookieStore.isCookieReplayEnabledForMethod(methodName) && log.isDebugEnabled()) {
            log.debug("Cookie replay disabled for mutating request {} {}", request.getMethod(), request.getUri());
        }
    }

    private void captureResponseCookies(String methodName, HttpResponse<?> response) {
        List<String> setCookieHeaders = response.getHeaders().getAll(HttpHeaders.SET_COOKIE);
        cookieStore.captureResponseCookies(methodName, setCookieHeaders);
    }
}
