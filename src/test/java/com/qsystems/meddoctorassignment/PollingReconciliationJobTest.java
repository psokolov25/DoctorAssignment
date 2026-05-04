package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.cache.BranchCacheUpdater;
import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.MedRobotProperties;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.domain.model.VisitUnservedService;
import com.qsystems.meddoctorassignment.domain.service.AutonomousMedicalExamAssignmentService;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultDoctorAvailableServicesResolver;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultDoctorServiceMatcher;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultUnknownDoctorQueueVisitProvider;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultVisitAssignmentExecutor;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultVisitRouteAnalyzer;
import com.qsystems.meddoctorassignment.domain.service.impl.MedRobotAwareDoctorServiceSelectionService;
import com.qsystems.meddoctorassignment.schedule.PollingReconciliationJob;
import com.qsystems.meddoctorassignment.support.InMemoryTestGateways;
import com.qsystems.meddoctorassignment.util.BranchLockManager;
import com.qsystems.meddoctorassignment.util.ProcessedVisitRegistry;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Проверяет, что polling-режим можно явно включать и выключать независимо от websocket. */
public class PollingReconciliationJobTest {

  @Test
  void skipsPollingCycleWhenPollingIsDisabled() {
    Fixture fixture = new Fixture();
    fixture.prepareBranchTopology();
    fixture.prepareWaitingVisit(6001L);
    fixture.assignmentProperties.setPollingEnabled(false);

    fixture.job.reconcile();

    Assertions.assertTrue(fixture.gateways.assignedOperations.isEmpty());
    Assertions.assertTrue(fixture.gateways.transferredOperations.isEmpty());
  }

  @Test
  void processesOpenRuntimeDoctorWhenPollingIsEnabled() {
    Fixture fixture = new Fixture();
    fixture.prepareBranchTopology();
    fixture.prepareWaitingVisit(6002L);
    fixture.assignmentProperties.setPollingEnabled(true);

    fixture.job.reconcile();

    Assertions.assertEquals(1, fixture.gateways.assignedOperations.size());
    Assertions.assertTrue(fixture.gateways.assignedOperations.get(0).contains("6002|301|45|41220000000007"));
    Assertions.assertEquals(1, fixture.gateways.transferredOperations.size());
    Assertions.assertTrue(fixture.gateways.transferredOperations.get(0).endsWith("|901"));
  }

  @Test
  void pollingAppliesOneBranchLimitAndBalancesSelectedVisitsAcrossAvailableDoctors() {
    Fixture fixture = new Fixture();
    fixture.prepareBranchTopology();
    fixture.assignmentProperties.setPollingEnabled(true);
    fixture.assignmentProperties.setMaxVisitsPerCycle(3);
    fixture.addSecondDoctorWithDedicatedQueue();
    fixture.prepareWaitingVisitsForBalancing();

    fixture.job.reconcile();

    Assertions.assertEquals(Arrays.asList("7|9005", "7|9002", "7|9003"), fixture.gateways.visitDetailsRequests);
    Assertions.assertFalse(fixture.gateways.visitDetailsRequests.contains("7|9004"));
    Assertions.assertFalse(fixture.gateways.visitDetailsRequests.contains("7|9001"));
    Assertions.assertEquals(Arrays.asList(
        "7|9005|301|45|41220000000007",
        "7|9002|302|46|41220000000008",
        "7|9003|301|45|41220000000007"
    ), fixture.gateways.assignedOperations);
    Assertions.assertEquals(Arrays.asList(
        "7|900|9005|901",
        "7|900|9002|902",
        "7|900|9003|901"
    ), fixture.gateways.transferredOperations);
    Assertions.assertEquals(1, fixture.gateways.waitingVisitRequests.size());
  }

  static final class Fixture {
    final InMemoryTestGateways gateways = new InMemoryTestGateways();
    final OrchestraDataCacheContainer cacheContainer = new OrchestraDataCacheContainer();
    final AssignmentProperties assignmentProperties = new AssignmentProperties();
    final MedRobotProperties medRobotProperties = new MedRobotProperties();
    final OrchestraProperties orchestraProperties = new OrchestraProperties();
    final BranchCacheUpdater updater = new BranchCacheUpdater(gateways, cacheContainer, assignmentProperties);
    final OrchestraDataCacheUpdateService updateService =
        new OrchestraDataCacheUpdateService(
            updater,
            cacheContainer,
            orchestraProperties,
            assignmentProperties,
            new com.qsystems.meddoctorassignment.branchgetter.AllBranchesGetter(gateways),
            new com.qsystems.meddoctorassignment.branchgetter.DefinedBranchesGetter(orchestraProperties));
    final AutonomousMedicalExamAssignmentService service =
        new AutonomousMedicalExamAssignmentService(
            updateService,
            cacheContainer,
            new DefaultDoctorAvailableServicesResolver(),
            new DefaultUnknownDoctorQueueVisitProvider(gateways),
            new DefaultVisitRouteAnalyzer(gateways),
            new MedRobotAwareDoctorServiceSelectionService(
                new DefaultDoctorServiceMatcher(assignmentProperties), gateways, medRobotProperties),
            new DefaultVisitAssignmentExecutor(gateways, assignmentProperties),
            gateways,
            new BranchLockManager(),
            new ProcessedVisitRegistry(),
            assignmentProperties);
    final PollingReconciliationJob job =
        new PollingReconciliationJob(cacheContainer, updateService, service, assignmentProperties);

    void prepareBranchTopology() {
      orchestraProperties.setBranchesForCache("7");
      assignmentProperties.setDryRun(false);
      assignmentProperties.setUnknownDoctorQueueId(900);

      com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData service =
          new com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData();
      service.setId(301);
      service.setInternalName("doctor-consultation");
      gateways.servicesByBranch.put(7, Collections.singletonList(service));

      com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue unknown =
          new com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue();
      unknown.setId(900);
      unknown.setName("врач не назначен");

      com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue doctorQueue =
          new com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue();
      doctorQueue.setId(901);
      doctorQueue.setName("кабинет врача");

      gateways.allQueuesByBranch.put(7, Arrays.asList(unknown, doctorQueue));
      gateways.queueByBranchAndService.put("7|301", doctorQueue);

      com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData profile =
          new com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData();
      profile.setId(15);
      profile.setName("Терапевт ВЭК, Цеховой Терапевт");
      gateways.workProfilesByBranch.put(7, Collections.singletonList(profile));
      gateways.workProfileQueues.put("7|15", Collections.singletonList(doctorQueue));

      com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData servicePoint =
          new com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData();
      servicePoint.setId(41220000000007L);
      servicePoint.setBranchId(7);
      servicePoint.setStaffId(45);
      servicePoint.setWorkProfileId(15);
      servicePoint.setStatus("OPEN");
      gateways.servicePointsByBranch.put(7, Collections.singletonList(servicePoint));

      updateService.refreshConfiguredBranches();
    }

    void addSecondDoctorWithDedicatedQueue() {
      com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue secondQueue =
          new com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue();
      secondQueue.setId(902);
      secondQueue.setName("кабинет врача 2");

      com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache cache =
          cacheContainer.getOrCreateBranchCache(7);
      cache.getQueueMap().put(Integer.valueOf(902), secondQueue);
      cache.getServiceIdToQueueId().put(Integer.valueOf(302), Integer.valueOf(902));

      java.util.Set<Integer> serviceIds = new java.util.HashSet<Integer>();
      serviceIds.add(Integer.valueOf(302));
      cache.getQueueIdToServiceIds().put(Integer.valueOf(902), serviceIds);

      java.util.Set<Integer> workProfileQueues = new java.util.HashSet<Integer>();
      workProfileQueues.add(Integer.valueOf(902));
      cache.getWorkProfileToQueueIds().put(Integer.valueOf(16), workProfileQueues);
      cache.getServicePointRuntimeStateMap().put(
          Long.valueOf(41220000000008L),
          new com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState(
              41220000000008L, 7, 46, 16, "OPEN"));
    }

    void prepareWaitingVisitsForBalancing() {
      gateways.waitingVisitsByQueue.put(
          "7|900",
          Arrays.asList(
              visit(9001L, 900, "A-9001", null),
              visit(9002L, 900, "A-9002", 30),
              visit(9003L, 900, "A-9003", 30),
              visit(9004L, 900, "A-9004", 5),
              visit(9005L, 900, "A-9005", 60)));
      addBalancingVisitRoute(9001L, null);
      addBalancingVisitRoute(9002L, 30);
      addBalancingVisitRoute(9003L, 30);
      addBalancingVisitRoute(9004L, 5);
      addBalancingVisitRoute(9005L, 60);
    }

    private void addBalancingVisitRoute(long visitId, Integer waitingTime) {
      gateways.visitDetailsById.put(
          Long.valueOf(visitId),
          new VisitDetails(
              visitId,
              900,
              Arrays.asList(
                  new VisitUnservedService(301, null, 1),
                  new VisitUnservedService(302, null, 1))));
      gateways.visitById.put(Long.valueOf(visitId), visit(visitId, 900, "A-" + visitId, waitingTime));
    }

    private VisitSummary visit(long id, Integer queueId, String ticketNumber, Integer waitingTime) {
      VisitSummary visitSummary = new VisitSummary(id, queueId, "WAITING", ticketNumber);
      visitSummary.setWaitingTime(waitingTime);
      return visitSummary;
    }

    void prepareWaitingVisit(long visitId) {
      gateways.waitingVisitsByQueue.put(
          "7|900", Collections.singletonList(new VisitSummary(visitId, 900, "WAITING", "A-" + visitId)));
      gateways.visitDetailsById.put(
          Long.valueOf(visitId),
          new VisitDetails(visitId, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
      gateways.visitById.put(
          Long.valueOf(visitId), new VisitSummary(visitId, 900, "WAITING", "A-" + visitId));
    }
  }
}
