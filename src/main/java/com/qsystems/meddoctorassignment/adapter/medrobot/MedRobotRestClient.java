package com.qsystems.meddoctorassignment.adapter.medrobot;

import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.annotation.Client;
import java.util.Set;
import org.reactivestreams.Publisher;

/** Декларативный REST-клиент к med-robot. */
@Client("${application.med-robot.url}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface MedRobotRestClient {

  /**
   * Запрашивает у med-robot оптимальную услугу и очередь по контракту
   * {@code /prorobot/optimalqueue/{branchId}/service/{serviceId}}.
   */
  @Post("${application.med-robot.optimal-service-path}")
  @SingleResult
  Publisher<HttpResponse<MedRobotOptimalServiceResponse>> selectOptimalService(
      int branchId, int serviceId, @Body Set<Integer> unservedServiceIds);
}
