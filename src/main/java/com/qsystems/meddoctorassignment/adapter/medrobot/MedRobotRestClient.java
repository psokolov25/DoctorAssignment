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
public interface MedRobotRestClient {

  /**
   * Запрашивает у med-robot оптимальную услугу и очередь по JSON-контракту:
   * {@code Content-Type: application/json}, тело — массив id непройденных услуг.
   */
  @Post("${application.med-robot.optimal-service-path}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @SingleResult
  Publisher<HttpResponse<MedRobotOptimalServiceResponse>> selectOptimalServiceByUnservedServices(
      int branchId, int serviceId, @Body Set<Integer> unservedServiceIds);

  /**
   * Запрашивает у med-robot оптимальную услугу и очередь по text/plain-контракту:
   * {@code Content-Type: text/plain}, тело — строковый идентификатор пациента/талона.
   *
   * <p>В Micronaut declarative client {@link Produces} управляет request {@code Content-Type},
   * а {@link Consumes} — request {@code Accept}. Поэтому для второй REST-точки med-robot
   * принципиально важно указывать {@code @Produces(text/plain)}, иначе сервер отвечает 415.
   */
  @Post("${application.med-robot.optimal-service-path}")
  @Produces(MediaType.TEXT_PLAIN)
  @Consumes(MediaType.APPLICATION_JSON)
  @SingleResult
  Publisher<HttpResponse<MedRobotOptimalServiceResponse>> selectOptimalServiceByPlainText(
      int branchId,
      int serviceId,
      @Body String plainTextBody,
      @QueryValue("policy") String policy);
}
