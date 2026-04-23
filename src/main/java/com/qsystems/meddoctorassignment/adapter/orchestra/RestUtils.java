package com.qsystems.meddoctorassignment.adapter.orchestra;

import io.micronaut.context.annotation.Context;
import io.micronaut.http.HttpResponse;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/** Небольшой вспомогательный компонент для безопасного синхронного чтения реактивного ответа. */
@Context
public class RestUtils {

  public <T> T handleReactiveResponseWithBlock(Publisher<HttpResponse<T>> response) {
    HttpResponse<T> httpResponse = Mono.from(response).block();
    if (httpResponse == null || !httpResponse.getBody().isPresent()) {
      throw new IllegalStateException("Reactive REST response does not contain a body");
    }
    return httpResponse.getBody().get();
  }
}
