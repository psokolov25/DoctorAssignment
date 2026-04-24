package com.qsystems.meddoctorassignment.adapter.gateway.impl;

import com.qsystems.meddoctorassignment.adapter.gateway.MedRobotOptimalServiceGateway;
import com.qsystems.meddoctorassignment.adapter.medrobot.MedRobotRestClient;
import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import com.qsystems.meddoctorassignment.adapter.orchestra.RestUtils;
import jakarta.inject.Singleton;
import java.util.Set;

/** Production-реализация шлюза к med-robot поверх Micronaut HTTP client. */
@Singleton
public class MedRobotOptimalServiceGatewayImpl implements MedRobotOptimalServiceGateway {

  private final MedRobotRestClient medRobotRestClient;
  private final RestUtils restUtils;

  public MedRobotOptimalServiceGatewayImpl(
      MedRobotRestClient medRobotRestClient, RestUtils restUtils) {
    this.medRobotRestClient = medRobotRestClient;
    this.restUtils = restUtils;
  }

  @Override
  public MedRobotOptimalServiceResponse selectOptimalService(
      int branchId, int currentServiceId, Set<Integer> unservedServiceIds) {
    return restUtils.handleReactiveResponseWithBlock(
        medRobotRestClient.selectOptimalService(branchId, currentServiceId, unservedServiceIds));
  }
}
