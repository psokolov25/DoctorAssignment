package com.qsystems.meddoctorassignment.adapter.gateway.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.MedRobotOptimalServiceGateway;
import com.qsystems.meddoctorassignment.adapter.medrobot.MedRobotRestClient;
import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import com.qsystems.meddoctorassignment.adapter.orchestra.RestUtils;
import com.qsystems.meddoctorassignment.config.MedRobotProperties;
import com.qsystems.meddoctorassignment.config.MedRobotRequestBodyMode;
import jakarta.inject.Singleton;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Production-реализация шлюза к med-robot поверх Micronaut HTTP client. */
@Singleton
public class MedRobotOptimalServiceGatewayImpl implements MedRobotOptimalServiceGateway {

  private static final Logger log = LoggerFactory.getLogger(MedRobotOptimalServiceGatewayImpl.class);

  private final MedRobotRestClient medRobotRestClient;
  private final RestUtils restUtils;
  private final MedRobotProperties medRobotProperties;

  public MedRobotOptimalServiceGatewayImpl(
      MedRobotRestClient medRobotRestClient,
      RestUtils restUtils,
      MedRobotProperties medRobotProperties) {
    this.medRobotRestClient = medRobotRestClient;
    this.restUtils = restUtils;
    this.medRobotProperties = medRobotProperties;
  }

  @Override
  public MedRobotOptimalServiceResponse selectOptimalService(
      int branchId, int currentServiceId, Set<Integer> unservedServiceIds, String ticketNumber) {
    if (medRobotProperties.getRequestBodyMode() == MedRobotRequestBodyMode.TICKET_NUMBER_PLAIN_TEXT) {
      String normalizedTicket = ticketNumber != null ? ticketNumber.trim() : null;
      if (normalizedTicket == null || normalizedTicket.isEmpty()) {
        throw new IllegalArgumentException("Ticket number is required for med-robot plain text mode");
      }
      log.info(
          "Request med-robot optimal service branch={} currentService={} bodyMode={} contentType=text/plain accept=application/json ticketNumber={} policy={}",
          Integer.valueOf(branchId),
          Integer.valueOf(currentServiceId),
          medRobotProperties.getRequestBodyMode(),
          normalizedTicket,
          medRobotProperties.getPlainTextPolicy());
      return restUtils.handleReactiveResponseWithBlock(
          medRobotRestClient.selectOptimalServicePlainText(
              branchId, currentServiceId, medRobotProperties.getPlainTextPolicy(), normalizedTicket));
    }

    log.info(
        "Request med-robot optimal service branch={} currentService={} bodyMode={} contentType=application/json accept=application/json unservedServices={}",
        Integer.valueOf(branchId),
        Integer.valueOf(currentServiceId),
        medRobotProperties.getRequestBodyMode(),
        unservedServiceIds);
    return restUtils.handleReactiveResponseWithBlock(
        medRobotRestClient.selectOptimalServiceJson(branchId, currentServiceId, unservedServiceIds));
  }
}
