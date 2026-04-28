package com.qsystems.meddoctorassignment.adapter.medrobot;

import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import java.util.Set;
import org.reactivestreams.Publisher;

/** Декларативный REST-клиент к med-robot. */
@Client("${application.med-robot.url}")
@Consumes(MediaType.APPLICATION_JSON)
public interface MedRobotRestClient {

  /**
   * Старый контракт: JSON-массив id непройденных услуг.
   *
   * <p>В Micronaut declarative client {@link Produces} задает {@code Content-Type} исходящего
   * запроса, а {@link Consumes} — {@code Accept} ожидаемого ответа. Поэтому для JSON-режима
   * должны быть выставлены {@code Content-Type: application/json} и {@code Accept: application/json}.</p>
   */
  @Post("${application.med-robot.optimal-service-path}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @SingleResult
  Publisher<HttpResponse<MedRobotOptimalServiceResponse>> selectOptimalServiceJson(
      int branchId, int serviceId, @Body Set<Integer> unservedServiceIds);

  /**
   * Новый контракт: номер талона в text/plain.
   *
   * <p>Важно: для Micronaut client {@link Produces} управляет именно {@code Content-Type}
   * запроса. Если здесь использовать только {@code @Consumes(text/plain)}, клиент начнет
   * отправлять {@code Accept: text/plain}, но тело останется с {@code Content-Type:
   * application/json}; med-robot в этом случае отвечает {@code 415 Unsupported Media Type}.</p>
   */
  @Post("${application.med-robot.optimal-service-path}{?policy}")
  @Produces(MediaType.TEXT_PLAIN)
  @Consumes(MediaType.APPLICATION_JSON)
  @SingleResult
  Publisher<HttpResponse<MedRobotOptimalServiceResponse>> selectOptimalServicePlainText(
      int branchId, int serviceId, @QueryValue String policy, @Body String ticketNumber);
}
