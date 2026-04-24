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
