package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.cache.BranchCacheUpdater;
import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
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

    static final class Fixture {
        final InMemoryTestGateways gateways = new InMemoryTestGateways();
        final OrchestraDataCacheContainer cacheContainer = new OrchestraDataCacheContainer();
        final AssignmentProperties assignmentProperties = new AssignmentProperties();
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
                new DefaultDoctorServiceMatcher(assignmentProperties),
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
