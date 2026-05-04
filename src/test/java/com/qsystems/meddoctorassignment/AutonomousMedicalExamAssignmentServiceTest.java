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
    void appliesLimiterWindowFromPollingCronMinutesStep() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setMaxVisitsPerCycle(3);
        fixture.assignmentProperties.setPollingCron("0 */2 * * * ?");

        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                new VisitSummary(6101L, 900, "WAITING", "A-6101"),
                new VisitSummary(6102L, 900, "WAITING", "A-6102"),
                new VisitSummary(6103L, 900, "WAITING", "A-6103")
        ));
        fixture.gateways.visitDetailsById.put(6101L, new VisitDetails(6101L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitDetailsById.put(6102L, new VisitDetails(6102L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitDetailsById.put(6103L, new VisitDetails(6103L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitById.put(6101L, new VisitSummary(6101L, 900, "WAITING", "A-6101"));
        fixture.gateways.visitById.put(6102L, new VisitSummary(6102L, 900, "WAITING", "A-6102"));
        fixture.gateways.visitById.put(6103L, new VisitSummary(6103L, 900, "WAITING", "A-6103"));

        DoctorContext context = fixture.openDoctor(7, 41220000000007L, 45, 15);
        fixture.service.process(context);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                new VisitSummary(6201L, 900, "WAITING", "A-6201"),
                new VisitSummary(6202L, 900, "WAITING", "A-6202"),
                new VisitSummary(6203L, 900, "WAITING", "A-6203")
        ));
        fixture.gateways.visitDetailsById.put(6201L, new VisitDetails(6201L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitDetailsById.put(6202L, new VisitDetails(6202L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitDetailsById.put(6203L, new VisitDetails(6203L, 900, Arrays.asList(new VisitUnservedService(301, null, 1))));
        fixture.gateways.visitById.put(6201L, new VisitSummary(6201L, 900, "WAITING", "A-6201"));
        fixture.gateways.visitById.put(6202L, new VisitSummary(6202L, 900, "WAITING", "A-6202"));
        fixture.gateways.visitById.put(6203L, new VisitSummary(6203L, 900, "WAITING", "A-6203"));

        fixture.service.process(context);

        Assertions.assertEquals(3, fixture.gateways.assignedOperations.size());
        Assertions.assertEquals(3, fixture.gateways.transferredOperations.size());
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

    @Test
    void medRobotSelectionForNextVisitAccountsForPreviouslyTransferredVisits() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setMaxVisitsPerCycle(2);
        fixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.ID_ASC);
        fixture.medRobotProperties.setEnabled(true);

        fixture.addDoctorServiceToCachedTopology(302, 902, "кабинет врача 2");
        fixture.gateways.medRobotSequentialByTransferCount = true;
        fixture.gateways.medRobotSequentialResponses.add(medRobotResponse(301, 901));
        fixture.gateways.medRobotSequentialResponses.add(medRobotResponse(302, 902));

        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                visit(8201L, 900, "A-8201", 20),
                visit(8202L, 900, "A-8202", 10)
        ));
        fixture.gateways.visitDetailsById.put(8201L, new VisitDetails(8201L, 900, Arrays.asList(
                new VisitUnservedService(301, null, 1),
                new VisitUnservedService(302, null, 2)
        )));
        fixture.gateways.visitDetailsById.put(8202L, new VisitDetails(8202L, 900, Arrays.asList(
                new VisitUnservedService(301, null, 1),
                new VisitUnservedService(302, null, 2)
        )));
        fixture.gateways.visitById.put(8201L, visit(8201L, 900, "A-8201", 20));
        fixture.gateways.visitById.put(8202L, visit(8202L, 900, "A-8202", 10));

        fixture.service.process(fixture.openDoctor(7, 41220000000007L, 45, 15));

        Assertions.assertEquals(2, fixture.gateways.medRobotRequests.size());
        Assertions.assertEquals(Arrays.asList(
                "7|8201|301|45|41220000000007",
                "7|8202|302|45|41220000000007"
        ), fixture.gateways.assignedOperations);
        Assertions.assertEquals(Arrays.asList(
                "7|900|8201|901",
                "7|900|8202|902"
        ), fixture.gateways.transferredOperations);
    }

    @Test
    void readsVisitsOnlyFromConfiguredUnknownDoctorQueue() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setMaxVisitsPerCycle(3);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(visit(6101L, 900, "A-6101", 10)));
        fixture.gateways.waitingVisitsByQueue.put("7|999", Collections.singletonList(visit(9999L, 999, "FOREIGN", 999)));
        fixture.addVisitRoute(6101L, 900, 301, 1);

        fixture.service.process(fixture.openDoctor(7, 41220000000007L, 45, 15));

        Assertions.assertEquals(Collections.singletonList("7|900"), fixture.gateways.waitingVisitRequests);
        Assertions.assertEquals(Collections.singletonList("7|6101"), fixture.gateways.visitDetailsRequests);
        Assertions.assertEquals(Collections.singletonList("7|6101|301|45|41220000000007"), fixture.gateways.assignedOperations);
        Assertions.assertEquals(Collections.singletonList("7|900|6101|901"), fixture.gateways.transferredOperations);
        Assertions.assertEquals(Integer.valueOf(901), fixture.gateways.visitById.get(Long.valueOf(6101L)).getQueueId());
    }

    @Test
    void sortsOldestFirstWithTieBreakAndAppliesLimitBeforeDetailsRead() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setMaxVisitsPerCycle(3);
        fixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.OLDEST_FIRST);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                visit(7001L, 900, "A-7001", null),
                visit(7002L, 900, "A-7002", 30),
                visit(7003L, 900, "A-7003", 30),
                visit(7004L, 900, "A-7004", 5),
                visit(7005L, 900, "A-7005", 60)
        ));
        fixture.addVisitRoutes(900, 7001L, 7002L, 7003L, 7004L, 7005L);

        fixture.service.process(fixture.openDoctor(7, 41220000000007L, 45, 15));

        Assertions.assertEquals(Arrays.asList("7|7005", "7|7002", "7|7003"), fixture.gateways.visitDetailsRequests);
        Assertions.assertEquals(Arrays.asList(
                "7|7005|301|45|41220000000007",
                "7|7002|301|45|41220000000007",
                "7|7003|301|45|41220000000007"
        ), fixture.gateways.assignedOperations);
        Assertions.assertFalse(fixture.gateways.visitDetailsRequests.contains("7|7004"));
        Assertions.assertFalse(fixture.gateways.visitDetailsRequests.contains("7|7001"));
    }

    @Test
    void sortsNewestFirstWithNullWaitingTimeLastAndReverseIdTieBreak() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setMaxVisitsPerCycle(4);
        fixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.NEWEST_FIRST);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                visit(7101L, 900, "A-7101", 20),
                visit(7102L, 900, "A-7102", 5),
                visit(7103L, 900, "A-7103", 5),
                visit(7104L, 900, "A-7104", null)
        ));
        fixture.addVisitRoutes(900, 7101L, 7102L, 7103L, 7104L);

        fixture.service.process(fixture.openDoctor(7, 41220000000007L, 45, 15));

        Assertions.assertEquals(Arrays.asList("7|7103", "7|7102", "7|7101", "7|7104"), fixture.gateways.visitDetailsRequests);
    }

    @Test
    void keepsAsReturnedOrderWhenConfigured() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setMaxVisitsPerCycle(3);
        fixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.AS_RETURNED);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                visit(7203L, 900, "A-7203", 100),
                visit(7201L, 900, "A-7201", 1),
                visit(7202L, 900, "A-7202", 50)
        ));
        fixture.addVisitRoutes(900, 7201L, 7202L, 7203L);

        fixture.service.process(fixture.openDoctor(7, 41220000000007L, 45, 15));

        Assertions.assertEquals(Arrays.asList("7|7203", "7|7201", "7|7202"), fixture.gateways.visitDetailsRequests);
    }

    @Test
    void supportsIdBasedSortingModes() {
        Fixture ascFixture = new Fixture();
        ascFixture.prepareBranchTopology();
        ascFixture.assignmentProperties.setDryRun(false);
        ascFixture.assignmentProperties.setMaxVisitsPerCycle(3);
        ascFixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.ID_ASC);
        ascFixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                visit(7303L, 900, "A-7303", 1),
                visit(7301L, 900, "A-7301", 100),
                visit(7302L, 900, "A-7302", 50)
        ));
        ascFixture.addVisitRoutes(900, 7301L, 7302L, 7303L);
        ascFixture.service.process(ascFixture.openDoctor(7, 41220000000007L, 45, 15));
        Assertions.assertEquals(Arrays.asList("7|7301", "7|7302", "7|7303"), ascFixture.gateways.visitDetailsRequests);

        Fixture descFixture = new Fixture();
        descFixture.prepareBranchTopology();
        descFixture.assignmentProperties.setDryRun(false);
        descFixture.assignmentProperties.setMaxVisitsPerCycle(3);
        descFixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.ID_DESC);
        descFixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                visit(7401L, 900, "A-7401", 100),
                visit(7403L, 900, "A-7403", 1),
                visit(7402L, 900, "A-7402", 50)
        ));
        descFixture.addVisitRoutes(900, 7401L, 7402L, 7403L);
        descFixture.service.process(descFixture.openDoctor(7, 41220000000007L, 45, 15));
        Assertions.assertEquals(Arrays.asList("7|7403", "7|7402", "7|7401"), descFixture.gateways.visitDetailsRequests);
    }

    @Test
    void performsFullSeatingWorkflowWithRouteSelectionRecheckAndPostCheck() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.addDoctorServiceToCachedTopology(302, 902, "кабинет врача 2");
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setMaxVisitsPerCycle(1);
        fixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.OLDEST_FIRST);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(visit(8001L, 900, "A-8001", 120)));
        fixture.gateways.visitDetailsById.put(Long.valueOf(8001L), new VisitDetails(8001L, 900, Arrays.asList(
                new VisitUnservedService(301, null, 2),
                new VisitUnservedService(302, null, 1)
        )));
        fixture.gateways.visitById.put(Long.valueOf(8001L), visit(8001L, 900, "A-8001", 120));

        fixture.service.process(fixture.openDoctor(7, 41220000000007L, 45, 15));

        Assertions.assertEquals(Collections.singletonList("7|8001"), fixture.gateways.visitDetailsRequests);
        Assertions.assertEquals(Arrays.asList("7|8001", "7|8001"), fixture.gateways.findVisitRequests);
        Assertions.assertEquals(Collections.singletonList("7|8001|302|45|41220000000007"), fixture.gateways.assignedOperations);
        Assertions.assertEquals(Collections.singletonList("7|900|8001|902"), fixture.gateways.transferredOperations);
        Assertions.assertEquals(Integer.valueOf(902), fixture.gateways.visitById.get(Long.valueOf(8001L)).getQueueId());
        Assertions.assertEquals(Arrays.asList(
                "getWaitingVisits|7|900",
                "getVisitDetails|7|8001",
                "findVisit|7|8001",
                "assignServiceToVisit|7|8001|302|45|41220000000007",
                "transferVisitToQueue|7|900|8001|902",
                "findVisit|7|8001"
        ), fixture.gateways.workflowOperations);
    }

    @Test
    void refreshesBranchSnapshotAfterEachSuccessfulAssignment() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setStaleCacheDurationSeconds(0);
        fixture.assignmentProperties.setMaxVisitsPerCycle(2);
        fixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.ID_ASC);

        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                visit(8101L, 900, "A-8101", 20),
                visit(8102L, 900, "A-8102", 10)
        ));
        fixture.addVisitRoutes(900, 8101L, 8102L);

        fixture.service.process(fixture.openDoctor(7, 41220000000007L, 45, 15));

        Assertions.assertEquals(2, fixture.gateways.assignedOperations.size());
        Assertions.assertEquals(2, fixture.gateways.transferredOperations.size());
        Assertions.assertTrue(fixture.gateways.allQueuesReadCount >= 2);
    }

    @Test
    void processesOnlyThreeOldestVisitsFromSevenInLocalMode() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setMaxVisitsPerCycle(3);
        fixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.OLDEST_FIRST);

        populateSevenVisitQueue(fixture, 8300L, new int[] {5, 90, 40, 80, 10, 70, 20});

        fixture.service.process(fixture.openDoctor(7, 41220000000007L, 45, 15));

        Assertions.assertEquals(Arrays.asList("7|8302", "7|8304", "7|8306"), fixture.gateways.visitDetailsRequests);
        Assertions.assertEquals(Arrays.asList(
                "7|8302|301|45|41220000000007",
                "7|8304|301|45|41220000000007",
                "7|8306|301|45|41220000000007"
        ), fixture.gateways.assignedOperations);
        Assertions.assertEquals(3, fixture.gateways.transferredOperations.size());
    }

    @Test
    void processesOnlyThreeOldestVisitsFromSevenWithMedRobot() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setMaxVisitsPerCycle(3);
        fixture.assignmentProperties.setVisitProcessingSortOrder(VisitProcessingSortOrder.OLDEST_FIRST);
        fixture.medRobotProperties.setEnabled(true);
        fixture.addDoctorServiceToCachedTopology(302, 902, "кабинет врача 2");
        fixture.gateways.medRobotResponses.put("7|301", medRobotResponse(302, 902));

        populateSevenVisitQueue(fixture, 8400L, new int[] {15, 95, 35, 85, 25, 75, 5});
        attachMedRobotRouteDataForSevenVisits(fixture, 8400L);

        fixture.service.process(fixture.openDoctor(7, 41220000000007L, 45, 15));

        Assertions.assertEquals(Arrays.asList("7|8402", "7|8404", "7|8406"), fixture.gateways.visitDetailsRequests);
        Assertions.assertEquals(3, fixture.gateways.medRobotRequests.size());
        Assertions.assertEquals(Arrays.asList(
                "7|8402|302|45|41220000000007",
                "7|8404|302|45|41220000000007",
                "7|8406|302|45|41220000000007"
        ), fixture.gateways.assignedOperations);
        Assertions.assertEquals(Arrays.asList(
                "7|900|8402|902",
                "7|900|8404|902",
                "7|900|8406|902"
        ), fixture.gateways.transferredOperations);
    }

    private static VisitSummary visit(long id, Integer queueId, String ticketNumber, Integer waitingTime) {
        VisitSummary visitSummary = new VisitSummary(id, queueId, "WAITING", ticketNumber);
        visitSummary.setWaitingTime(waitingTime);
        return visitSummary;
    }

    private static MedRobotOptimalServiceResponse medRobotResponse(int serviceId, int queueId) {
        MedRobotOptimalServiceResponse response = new MedRobotOptimalServiceResponse();
        response.setServiceId(serviceId);
        response.setQueueId(queueId);
        return response;
    }

    private static VisitDetails visitDetailsWithServices(long visitId, int queueId, int firstServiceId, int secondServiceId) {
        return new VisitDetails(visitId, queueId, Arrays.asList(
                new VisitUnservedService(firstServiceId, null, 1),
                new VisitUnservedService(secondServiceId, null, 2)
        ));
    }

    private static void populateSevenVisitQueue(Fixture fixture, long idBase, int[] waitingTimes) {
        fixture.gateways.waitingVisitsByQueue.put("7|900", Arrays.asList(
                visit(idBase + 1L, 900, "A-" + (idBase + 1L), Integer.valueOf(waitingTimes[0])),
                visit(idBase + 2L, 900, "A-" + (idBase + 2L), Integer.valueOf(waitingTimes[1])),
                visit(idBase + 3L, 900, "A-" + (idBase + 3L), Integer.valueOf(waitingTimes[2])),
                visit(idBase + 4L, 900, "A-" + (idBase + 4L), Integer.valueOf(waitingTimes[3])),
                visit(idBase + 5L, 900, "A-" + (idBase + 5L), Integer.valueOf(waitingTimes[4])),
                visit(idBase + 6L, 900, "A-" + (idBase + 6L), Integer.valueOf(waitingTimes[5])),
                visit(idBase + 7L, 900, "A-" + (idBase + 7L), Integer.valueOf(waitingTimes[6]))
        ));
        fixture.addVisitRoutes(900, idBase + 1L, idBase + 2L, idBase + 3L, idBase + 4L, idBase + 5L, idBase + 6L, idBase + 7L);
    }

    private static void attachMedRobotRouteDataForSevenVisits(Fixture fixture, long idBase) {
        for (long visitId = idBase + 1L; visitId <= idBase + 7L; visitId++) {
            fixture.gateways.visitDetailsById.put(Long.valueOf(visitId), visitDetailsWithServices(visitId, 900, 301, 302));
            fixture.gateways.visitById.put(Long.valueOf(visitId), visit(visitId, 900, "A-" + visitId, 1));
        }
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

        void addVisitRoutes(int queueId, long... visitIds) {
            for (long visitId : visitIds) {
                addVisitRoute(visitId, queueId, 301, 1);
            }
        }

        void addVisitRoute(long visitId, int queueId, int serviceId, Integer routeOrder) {
            fixtureVisitDetails(visitId, queueId, serviceId, routeOrder);
            VisitSummary summary = new VisitSummary(visitId, queueId, "WAITING", "A-" + visitId);
            gateways.visitById.put(Long.valueOf(visitId), summary);
        }

        private void fixtureVisitDetails(long visitId, int queueId, int serviceId, Integer routeOrder) {
            gateways.visitDetailsById.put(Long.valueOf(visitId),
                    new VisitDetails(visitId, queueId, Arrays.asList(new VisitUnservedService(serviceId, null, routeOrder))));
        }

        void addDoctorServiceToCachedTopology(int serviceId, int queueId, String queueName) {
            com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue queue = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue();
            queue.setId(queueId);
            queue.setName(queueName);

            com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache cache = cacheContainer.getOrCreateBranchCache(7);
            cache.getQueueMap().put(Integer.valueOf(queueId), queue);
            cache.getServiceIdToQueueId().put(Integer.valueOf(serviceId), Integer.valueOf(queueId));

            java.util.Set<Integer> serviceIds = new java.util.HashSet<Integer>();
            serviceIds.add(Integer.valueOf(serviceId));
            cache.getQueueIdToServiceIds().put(Integer.valueOf(queueId), serviceIds);

            java.util.Set<Integer> workProfileQueues = cache.getWorkProfileToQueueIds().get(Integer.valueOf(15));
            if (workProfileQueues == null) {
                workProfileQueues = new java.util.HashSet<Integer>();
                cache.getWorkProfileToQueueIds().put(Integer.valueOf(15), workProfileQueues);
            }
            workProfileQueues.add(Integer.valueOf(queueId));
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
