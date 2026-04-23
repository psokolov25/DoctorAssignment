package com.qsystems.meddoctorassignment.adapter.orchestra;

import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.SmallBranch;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import java.util.Set;
import org.reactivestreams.Publisher;

/**
 * Декларативный Micronaut-клиент для подтвержденных REST endpoint-ов Orchestra.
 *
 * <p>Интерфейс оставлен реактивным, потому что повторяет стиль из исходного проекта {@code
 * med-robot}; синхронное ожидание ответа вынесено в {@link RestUtils}.
 */
@Client("${application.orchestra.url}")
@Consumes(MediaType.APPLICATION_JSON)
public interface OrchestraRestClient {

  @Get("${application.orchestra.configuration-rest-path}/branches")
  @SingleResult
  Publisher<HttpResponse<Set<SmallBranch>>> getAllBranches();

  @Get("${application.orchestra.common-rest-path}/servicepoint/branches/{branchId}/services")
  @SingleResult
  Publisher<HttpResponse<Set<ServiceData>>> getAllServicesFromBranch(int branchId);

  @Get(
      "${application.orchestra.common-rest-path}/entrypoint/branches/{branchId}/services/{serviceId}/queue")
  @SingleResult
  Publisher<HttpResponse<TinyQueue>> getQueueForServiceInBranch(int branchId, int serviceId);

  @Get(
      "${application.orchestra.common-rest-path}/managementinformation/v2/branches/{branchId}/servicePoints")
  @SingleResult
  Publisher<HttpResponse<Set<ServicePointData>>> getServicePointsFromBranch(int branchId);

  @Get("${application.orchestra.common-rest-path}/servicepoint/branches/{branchId}/workProfiles")
  @SingleResult
  Publisher<HttpResponse<Set<WorkProfileData>>> getAllWorkProfilesInBranch(int branchId);

  @Get(
      "${application.orchestra.common-rest-path}/servicepoint/branches/{branchId}/workProfiles/{workProfileId}/queues")
  @SingleResult
  Publisher<HttpResponse<Set<TinyQueue>>> getAllQueuesForWorkProfileInBranch(
      int branchId, int workProfileId);

  @Get("${application.orchestra.common-rest-path}/servicepoint/branches/{branchId}/queues/")
  @SingleResult
  Publisher<HttpResponse<Set<TinyQueue>>> getAllQueuesInBranch(int branchId);
}
