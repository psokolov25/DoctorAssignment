package com.qsystems.meddoctorassignment.adapter.medrobot;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import org.reactivestreams.Publisher;

/**
 * HTTP-фильтр для REST-вызовов med-robot.
 *
 * <p>Если в application.yml указаны {@code application.med-robot.username/password}, фильтр
 * добавляет Basic Authorization ко всем запросам к med-robot. Если авторизация на роботе не
 * используется, свойства можно оставить пустыми — фильтр не будет создан.</p>
 */
@Filter(value = "${application.med-robot.url}", patterns = {"/**"})
@Requires(property = "application.med-robot.username")
@Requires(property = "application.med-robot.password")
@Requires(property = "application.med-robot.url")
public class MedRobotRestConfiguration implements HttpClientFilter {

  private final String username;
  private final String password;

  public MedRobotRestConfiguration(
      @Value("${application.med-robot.username}") String username,
      @Value("${application.med-robot.password}") String password) {
    this.username = username;
    this.password = password;
  }

  @Override
  public Publisher<? extends HttpResponse<?>> doFilter(
      MutableHttpRequest<?> request, ClientFilterChain chain) {
    if (!request.getHeaders().contains(HttpHeaders.AUTHORIZATION)) {
      request.basicAuth(username, password);
    }
    return chain.proceed(request);
  }
}
