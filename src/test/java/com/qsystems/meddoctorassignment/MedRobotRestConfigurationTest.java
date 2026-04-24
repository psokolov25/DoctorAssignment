package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.adapter.medrobot.MedRobotRestConfiguration;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.ClientFilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/** Проверяет фильтр авторизации REST-вызовов к med-robot. */
public class MedRobotRestConfigurationTest {

  @Test
  void addsBasicAuthorizationWhenHeaderIsAbsent() {
    MedRobotRestConfiguration filter = new MedRobotRestConfiguration("robot", "secret");
    CapturingChain chain = new CapturingChain();

    filter.doFilter(HttpRequest.POST("/prorobot/optimalqueue/7/service/301", "[]"), chain);

    String expectedToken = Base64.getEncoder().encodeToString("robot:secret".getBytes(StandardCharsets.UTF_8));
    Assertions.assertEquals("Basic " + expectedToken, chain.request.getHeaders().get(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void keepsExistingAuthorizationHeader() {
    MedRobotRestConfiguration filter = new MedRobotRestConfiguration("robot", "secret");
    CapturingChain chain = new CapturingChain();
    MutableHttpRequest<String> request = HttpRequest.POST("/prorobot/optimalqueue/7/service/301", "[]");
    request.header(HttpHeaders.AUTHORIZATION, "Bearer existing-token");

    filter.doFilter(request, chain);

    Assertions.assertEquals("Bearer existing-token", chain.request.getHeaders().get(HttpHeaders.AUTHORIZATION));
  }

  private static final class CapturingChain implements ClientFilterChain {
    private MutableHttpRequest<?> request;

    @Override
    public Publisher<? extends HttpResponse<?>> proceed(MutableHttpRequest<?> request) {
      this.request = request;
      return Mono.just(HttpResponse.ok());
    }
  }
}
