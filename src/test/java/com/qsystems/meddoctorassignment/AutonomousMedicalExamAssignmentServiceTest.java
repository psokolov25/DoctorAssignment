package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.cache.BranchCacheUpdater;
import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.MedRobotProperties;
import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import com.qsystems.meddoctorassignment.config.VisitProcessingSortOrder;
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
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;
import com.qsystems.meddoctorassignment.support.InMemoryTestGateways;
import com.qsystems.meddoctorassignment.util.BranchLockManager;
import com.qsystems.meddoctorassignment.util.ProcessedVisitRegistry;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AutonomousMedicalExamAssignmentServiceTest {

    @Test
    void handlesEmptyQueueWithoutAssignments() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        Assertions.assertTrue(fixture.gateways.assignedOperations.isEmpty());
        Assertions.assertTrue(fixture.gateways.transferredOperations.isEmpty());
    }

    @Test
    void assignsMatchingVisitAndTransfersIt() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.getExperimentalEndpoints().setEnabled(true); // not used by fake gateway
        fixture.gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(new VisitSummary(1001L, 900, "WAITING", "A-001")));
        fixture.gateways.visitDetailsById.put(1001L, new VisitDetails(1001L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitById.put(1001L, new VisitSummary(1001L, 900, "WAITING", "A-001"));

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        Assertions.assertEquals(1, fixture.gateways.assignedOperations.size());
        Assertions.assertEquals(1, fixture.gateways.transferredOperations.size());
        Assertions.assertTrue(fixture.gateways.assignedOperations.get(0).contains("|301|45|41220000000007"));
        Assertions.assertTrue(fixture.gateways.transferredOperations.get(0).endsWith("|901"));
    }

    @Test
    void processesOnlyConfiguredNumberOfOldestVisitsFirst() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setMaxVisitsPerCycle(3);
        fixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.OLDEST_FIRST);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                visit(7001L, 900, "A-001", 5),
                visit(7002L, 900, "A-002", 40),
                visit(7003L, 900, "A-003", 10),
                visit(7004L, 900, "A-004", 30),
                visit(7005L, 900, "A-005", 20)
        ));

        for (long visitId = 7001L; visitId <= 7005L; visitId++) {
            fixture.gateways.visitDetailsById.put(Long.valueOf(visitId),
                    new VisitDetails(visitId, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
            fixture.gateways.visitById.put(Long.valueOf(visitId),
                    new VisitSummary(visitId, 900, "WAITING", "A-" + visitId));
        }

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        Assertions.assertEquals(3, fixture.gateways.assignedOperations.size());
        Assertions.assertTrue(fixture.gateways.assignedOperations.get(0).contains("|7002|"));
        Assertions.assertTrue(fixture.gateways.assignedOperations.get(1).contains("|7004|"));
        Assertions.assertTrue(fixture.gateways.assignedOperations.get(2).contains("|7005|"));
    }


    @Test
    void processesSameVisitAgainWhenCurrentVisitServiceRecordChanges() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(new VisitSummary(1003L, 900, "WAITING", "A-003")));
        fixture.gateways.visitById.put(1003L, new VisitSummary(1003L, 900, "WAITING", "A-003"));

        VisitDetails firstStep = new VisitDetails(1003L, 900, Arrays.asList(new VisitUnservedService(301, null, 1)));
        firstStep.setCurrentVisitServiceRecordId(Long.valueOf(7001L));
        fixture.gateways.visitDetailsById.put(1003L, firstStep);

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        VisitDetails secondStep = new VisitDetails(1003L, 900, Arrays.asList(new VisitUnservedService(301, null, 1)));
        secondStep.setCurrentVisitServiceRecordId(Long.valueOf(7002L));
        fixture.gateways.visitDetailsById.put(1003L, secondStep);
        fixture.service.process(context);

        Assertions.assertEquals(2, fixture.gateways.assignedOperations.size());
        Assertions.assertEquals(2, fixture.gateways.transferredOperations.size());
    }

    @Test
    void skipsSameVisitWhenCurrentVisitServiceRecordIsUnchangedInsideTtl() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(new VisitSummary(1004L, 900, "WAITING", "A-004")));
        fixture.gateways.visitById.put(1004L, new VisitSummary(1004L, 900, "WAITING", "A-004"));

        VisitDetails step = new VisitDetails(1004L, 900, Arrays.asList(new VisitUnservedService(301, null, 1)));
        step.setCurrentVisitServiceRecordId(Long.valueOf(7003L));
        fixture.gateways.visitDetailsById.put(1004L, step);

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);
        fixture.service.process(context);

        Assertions.assertEquals(1, fixture.gateways.assignedOperations.size());
        Assertions.assertEquals(1, fixture.gateways.transferredOperations.size());
    }

    @Test
    void skipsVisitWithoutMatchingService() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(new VisitSummary(1002L, 900, "WAITING", "A-002")));
        fixture.gateways.visitDetailsById.put(1002L, new VisitDetails(1002L, 900, Arrays.asList(new VisitUnservedService(999, null, 1))));
        fixture.gateways.visitById.put(1002L, new VisitSummary(1002L, 900, "WAITING", "A-002"));

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        Assertions.assertTrue(fixture.gateways.assignedOperations.isEmpty());
    }

    @Test
    void skipsRedundantAssignWhenVisitAlreadyHasSelectedServiceAndTransfersDirectly() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(new VisitSummary(1500L, 900, "WAITING", "A-1500")));
        VisitDetails visitDetails = new VisitDetails(1500L, 900, Arrays.asList(new VisitUnservedService(301, null, 1)));
        visitDetails.setCurrentServiceId(Integer.valueOf(301));
        fixture.gateways.visitDetailsById.put(1500L, visitDetails);
        fixture.gateways.visitById.put(1500L, new VisitSummary(1500L, 900, "WAITING", "A-1500"));

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        Assertions.assertTrue(fixture.gateways.assignedOperations.isEmpty());
        Assertions.assertEquals(1, fixture.gateways.transferredOperations.size());
        Assertions.assertTrue(fixture.gateways.transferredOperations.get(0).endsWith("|901"));
    }

    @Test
    void continuesWhenOneVisitFailsAndProcessesNextOne() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                new VisitSummary(2001L, 900, "WAITING", "A-003"),
                new VisitSummary(2002L, 900, "WAITING", "A-004")
        ));
        fixture.gateways.visitDetailsById.put(2001L, new VisitDetails(2001L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitDetailsById.put(2002L, new VisitDetails(2002L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitById.put(2001L, new VisitSummary(2001L, 900, "WAITING", "A-003"));
        fixture.gateways.visitById.put(2002L, new VisitSummary(2002L, 900, "WAITING", "A-004"));
        fixture.gateways.failVisitId = 2001L;

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        Assertions.assertEquals(1, fixture.gateways.assignedOperations.size());
        Assertions.assertTrue(fixture.gateways.assignedOperations.get(0).contains("2002"));
    }

    @Test
    void abortsRemainingVisitsWhenAssignDetectsInvalidMutationContext() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                new VisitSummary(3001L, 900, "WAITING", "A-005"),
                new VisitSummary(3002L, 900, "WAITING", "A-006")
        ));
        fixture.gateways.visitDetailsById.put(3001L, new VisitDetails(3001L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitDetailsById.put(3002L, new VisitDetails(3002L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitById.put(3001L, new VisitSummary(3001L, 900, "WAITING", "A-005"));
        fixture.gateways.visitById.put(3002L, new VisitSummary(3002L, 900, "WAITING", "A-006"));
        fixture.gateways.blockedAssignVisitId = 3001L;

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        Assertions.assertTrue(fixture.gateways.assignedOperations.isEmpty());
        Assertions.assertTrue(fixture.gateways.transferredOperations.isEmpty());
    }

    @Test
    void abortsBeforeFirstVisitWhenActivationStepFails() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.getActivation().setEnabled(true);
        fixture.gateways.activationEnabled = true;
        fixture.gateways.activationFail = true;

        fixture.gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(new VisitSummary(4001L, 900, "WAITING", "A-007")));
        fixture.gateways.visitDetailsById.put(4001L, new VisitDetails(4001L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitById.put(4001L, new VisitSummary(4001L, 900, "WAITING", "A-007"));

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        Assertions.assertEquals(1, fixture.gateways.activationOperations.size());
        Assertions.assertTrue(fixture.gateways.assignedOperations.isEmpty());
        Assertions.assertTrue(fixture.gateways.transferredOperations.isEmpty());
    }

    @Test
    void usesMedRobotSelectionWhenEnabled() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.medRobotProperties.setEnabled(true);

        com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue robotQueue = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue();
        robotQueue.setId(902);
        robotQueue.setName("оптимальная очередь робота");
        fixture.cacheContainer.getOrCreateBranchCache(7).getQueueMap().put(902, robotQueue);

        MedRobotOptimalServiceResponse response = new MedRobotOptimalServiceResponse();
        response.setServiceId(302);
        response.setQueueId(902);
        fixture.gateways.medRobotResponses.put("7|301", response);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(new VisitSummary(5001L, 900, "WAITING", "A-5001")));
        fixture.gateways.visitDetailsById.put(5001L, new VisitDetails(5001L, 900, Arrays.asList(
                new VisitUnservedService(301, null, 1),
                new VisitUnservedService(302, null, 2)
        )));
        fixture.gateways.visitById.put(5001L, new VisitSummary(5001L, 900, "WAITING", "A-5001"));

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        Assertions.assertEquals(1, fixture.gateways.medRobotRequests.size());
        Assertions.assertEquals(1, fixture.gateways.assignedOperations.size());
        Assertions.assertTrue(fixture.gateways.assignedOperations.get(0).contains("|302|45|41220000000007"));
        Assertions.assertEquals(1, fixture.gateways.transferredOperations.size());
        Assertions.assertTrue(fixture.gateways.transferredOperations.get(0).endsWith("|902"));
    }

    @Test
    void fallsBackToLocalSelectionWhenMedRobotFails() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.medRobotProperties.setEnabled(true);
        fixture.gateways.medRobotFail = true;

        fixture.gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(new VisitSummary(5002L, 900, "WAITING", "A-5002")));
        fixture.gateways.visitDetailsById.put(5002L, new VisitDetails(5002L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitById.put(5002L, new VisitSummary(5002L, 900, "WAITING", "A-5002"));

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        Assertions.assertEquals(1, fixture.gateways.medRobotRequests.size());
        Assertions.assertEquals(1, fixture.gateways.assignedOperations.size());
        Assertions.assertTrue(fixture.gateways.assignedOperations.get(0).contains("|301|45|41220000000007"));
        Assertions.assertEquals(1, fixture.gateways.transferredOperations.size());
        Assertions.assertTrue(fixture.gateways.transferredOperations.get(0).endsWith("|901"));
    }

    private static VisitSummary visit(long id, Integer queueId, String ticketNumber, Integer waitingTime) {
        VisitSummary visitSummary = new VisitSummary(id, queueId, "WAITING", ticketNumber);
        visitSummary.setWaitingTime(waitingTime);
        return visitSummary;
    }

    static final class Fixture {
        final InMemoryTestGateways gateways = new InMemoryTestGateways();
        final OrchestraDataCacheContainer cacheContainer = new OrchestraDataCacheContainer();
        final AssignmentProperties assignmentProperties = new AssignmentProperties();
        final MedRobotProperties medRobotProperties = new MedRobotProperties();
        final OrchestraProperties orchestraProperties = new OrchestraProperties();
        final BranchCacheUpdater updater = new BranchCacheUpdater(gateways, cacheContainer, assignmentProperties);
        final OrchestraDataCacheUpdateService updateService = new OrchestraDataCacheUpdateService(
                updater,
                cacheContainer,
                orchestraProperties,
                assignmentProperties,
                new com.qsystems.meddoctorassignment.branchgetter.AllBranchesGetter(gateways),
                new com.qsystems.meddoctorassignment.branchgetter.DefinedBranchesGetter(orchestraProperties)
        );
        final AutonomousMedicalExamAssignmentService service = new AutonomousMedicalExamAssignmentService(
                updateService,
                cacheContainer,
                new DefaultDoctorAvailableServicesResolver(),
                new DefaultUnknownDoctorQueueVisitProvider(gateways),
                new DefaultVisitRouteAnalyzer(gateways),
                new MedRobotAwareDoctorServiceSelectionService(
                        new DefaultDoctorServiceMatcher(assignmentProperties),
                        gateways,
                        medRobotProperties),
                new DefaultVisitAssignmentExecutor(gateways, assignmentProperties),
                gateways,
                new BranchLockManager(),
                new ProcessedVisitRegistry(),
                assignmentProperties
        );

        void prepareBranchTopology() {
            orchestraProperties.setBranchesForCache("7");
            assignmentProperties.setUnknownDoctorQueueId(900);

            com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData service = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData();
            service.setId(301);
            service.setInternalName("doctor-consultation");
            gateways.servicesByBranch.put(7, Collections.singletonList(service));

            com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue unknown = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue();
            unknown.setId(900);
            unknown.setName("врач не назначен");

            com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue doctorQueue = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue();
            doctorQueue.setId(901);
            doctorQueue.setName("кабинет врача");

            gateways.allQueuesByBranch.put(7, Arrays.asList(unknown, doctorQueue));
            gateways.queueByBranchAndService.put("7|301", doctorQueue);

            com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData profile = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData();
            profile.setId(15);
            profile.setName("Терапевт ВЭК, Цеховой Терапевт");
            gateways.workProfilesByBranch.put(7, Collections.singletonList(profile));
            gateways.workProfileQueues.put("7|15", Collections.singletonList(doctorQueue));

            com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData servicePoint = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData();
            servicePoint.setId(41220000000007L);
            servicePoint.setBranchId(7);
            servicePoint.setStaffId(45);
            servicePoint.setWorkProfileId(15);
            servicePoint.setStatus("OPEN");
            gateways.servicePointsByBranch.put(7, Collections.singletonList(servicePoint));

            updateService.refreshConfiguredBranches();
        }

        DoctorContext openDoctor(int branchId, long servicePointId, int staffId, int workProfileId) {
            DoctorContext context = new DoctorContext();
            context.setTriggerSource(TriggerSource.SERVICE_POINT_OPEN);
            context.setBranchId(branchId);
            context.setServicePointId(servicePointId);
            context.setStaffId(staffId);
            context.setWorkProfileId(workProfileId);
            context.recordSource("branchId", "test");
            context.recordSource("servicePointId", "test");
            context.recordSource("staffId", "test");
            context.recordSource("workProfileId", "test");
            return context;
        }
    }
}
