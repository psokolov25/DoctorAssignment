package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.branchgetter.AllBranchesGetter;
import com.qsystems.meddoctorassignment.branchgetter.DefinedBranchesGetter;
import com.qsystems.meddoctorassignment.cache.BranchCacheUpdater;
import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.MedRobotProperties;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import com.qsystems.meddoctorassignment.domain.service.AutonomousMedicalExamAssignmentService;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultDoctorAvailableServicesResolver;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultDoctorServiceMatcher;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultLoggedDoctorContextResolver;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultUnknownDoctorQueueVisitProvider;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultVisitAssignmentExecutor;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultVisitRouteAnalyzer;
import com.qsystems.meddoctorassignment.domain.service.impl.MedRobotAwareDoctorServiceSelectionService;
import com.qsystems.meddoctorassignment.event.DoctorAssignmentEventHandler;
import com.qsystems.meddoctorassignment.event.UserSessionReadinessCoordinator;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.support.InMemoryTestGateways;
import com.qsystems.meddoctorassignment.util.BranchLockManager;
import com.qsystems.meddoctorassignment.util.EventDeduplicator;
import com.qsystems.meddoctorassignment.util.ProcessedVisitRegistry;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DoctorAssignmentEventHandlerTriggerTest {

    @Test
    void skipsServicePointOpenWhenTriggerDisabled() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);
        fixture.assignmentProperties.setServicePointOpenTriggerEnabled(false);

        OrchestraEvent event = new OrchestraEvent();
        event.setEventName("SERVICE_POINT_OPEN");
        event.setUnitId(41220000000007L);
        event.getParameters().put("branchId", 7);
        event.getParameters().put("userId", 45);
        event.getParameters().put("workProfileOrigId", 15);
        event.getParameters().put("servicePointId", 41220000000007L);

        fixture.handler.handle(event);

        Assertions.assertTrue(fixture.gateways.assignedOperations.isEmpty());
        Assertions.assertTrue(fixture.gateways.transferredOperations.isEmpty());
    }

    @Test
    void userServicePointSessionStartAloneOnlyRegistersPendingSession() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);

        OrchestraEvent event = new OrchestraEvent();
        event.setEventName("USER_SERVICE_POINT_SESSION_START");
        event.setUnitId(41220000000007L);
        event.getParameters().put("branchId", 7);
        event.getParameters().put("userId", 45);
        event.getParameters().put("workProfileOrigId", 15);
        event.getParameters().put("servicePointId", 41220000000007L);
        event.getParameters().put("servicePointLogicId", 901);
        event.getParameters().put("staffTransactionId", 123456789L);

        fixture.handler.handle(event);

        Assertions.assertTrue(fixture.gateways.assignedOperations.isEmpty());
        Assertions.assertTrue(fixture.gateways.transferredOperations.isEmpty());
    }

    @Test
    void matchingSetWorkProfileCompletesPendingSessionAndTriggersAssignment() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);

        OrchestraEvent sessionStart = new OrchestraEvent();
        sessionStart.setEventName("USER_SERVICE_POINT_SESSION_START");
        sessionStart.setUnitId(41220000000007L);
        sessionStart.getParameters().put("branchId", 7);
        sessionStart.getParameters().put("userId", 45);
        sessionStart.getParameters().put("workProfileOrigId", 15);
        sessionStart.getParameters().put("servicePointId", 41220000000007L);
        sessionStart.getParameters().put("servicePointLogicId", 901);
        sessionStart.getParameters().put("staffTransactionId", 123456789L);

        OrchestraEvent setWorkProfile = new OrchestraEvent();
        setWorkProfile.setEventName("SET_WORK_PROFILE");
        setWorkProfile.setUnitId(41220000000007L);
        setWorkProfile.getParameters().put("branchId", 7);
        setWorkProfile.getParameters().put("userId", 45);
        setWorkProfile.getParameters().put("workProfileOrigId", 15);
        setWorkProfile.getParameters().put("servicePointId", 41220000000007L);
        setWorkProfile.getParameters().put("servicePointLogicId", 901);
        setWorkProfile.getParameters().put("staffTransactionId", 123456789L);

        fixture.handler.handle(sessionStart);
        fixture.handler.handle(setWorkProfile);

        Assertions.assertEquals(1, fixture.gateways.assignedOperations.size());
    }

    @Test
    void rawSetWorkProfileDoesNotTriggerWhenPendingSessionIsMissing() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopology();
        fixture.assignmentProperties.setDryRun(false);

        OrchestraEvent setWorkProfile = new OrchestraEvent();
        setWorkProfile.setEventName("SET_WORK_PROFILE");
        setWorkProfile.setUnitId(41220000000007L);
        setWorkProfile.getParameters().put("branchId", 7);
        setWorkProfile.getParameters().put("userId", 45);
        setWorkProfile.getParameters().put("workProfileOrigId", 15);
        setWorkProfile.getParameters().put("servicePointId", 41220000000007L);
        setWorkProfile.getParameters().put("servicePointLogicId", 901);
        setWorkProfile.getParameters().put("staffTransactionId", 123456789L);

        fixture.handler.handle(setWorkProfile);

        Assertions.assertTrue(fixture.gateways.assignedOperations.isEmpty());
    }

    static final class Fixture {
        final InMemoryTestGateways gateways = new InMemoryTestGateways();
        final OrchestraDataCacheContainer container = new OrchestraDataCacheContainer();
        final AssignmentProperties assignmentProperties = new AssignmentProperties();
        final OrchestraProperties orchestraProperties = new OrchestraProperties();
        final BranchCacheUpdater updater = new BranchCacheUpdater(gateways, container, assignmentProperties);
        final OrchestraDataCacheUpdateService updateService = new OrchestraDataCacheUpdateService(
                updater,
                container,
                orchestraProperties,
                assignmentProperties,
                new AllBranchesGetter(gateways),
                new DefinedBranchesGetter(orchestraProperties)
        );
        final AutonomousMedicalExamAssignmentService serviceLogic = new AutonomousMedicalExamAssignmentService(
                updateService,
                container,
                new DefaultDoctorAvailableServicesResolver(),
                new DefaultUnknownDoctorQueueVisitProvider(gateways),
                new DefaultVisitRouteAnalyzer(gateways),
                new MedRobotAwareDoctorServiceSelectionService(
                        new DefaultDoctorServiceMatcher(assignmentProperties),
                        gateways,
                        new MedRobotProperties()),
                new DefaultVisitAssignmentExecutor(gateways, assignmentProperties),
                gateways,
                new BranchLockManager(),
                new ProcessedVisitRegistry(),
                assignmentProperties
        );
        final DoctorAssignmentEventHandler handler = new DoctorAssignmentEventHandler(
                new EventDeduplicator(),
                new DefaultLoggedDoctorContextResolver(container, gateways),
                serviceLogic,
                assignmentProperties,
                new UserSessionReadinessCoordinator(assignmentProperties),
                new com.qsystems.meddoctorassignment.event.WorkProfileExpansionTriggerEvaluator(container, new DefaultLoggedDoctorContextResolver(container, gateways), new DefaultDoctorAvailableServicesResolver())
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
            profile.setName("Терапевт");
            gateways.workProfilesByBranch.put(7, Collections.singletonList(profile));
            gateways.workProfileQueues.put("7|15", Collections.singletonList(doctorQueue));

            com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData servicePoint = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData();
            servicePoint.setId(41220000000007L);
            servicePoint.setBranchId(7);
            servicePoint.setStaffId(45);
            servicePoint.setWorkProfileId(15);
            servicePoint.setStatus("OPEN");
            gateways.servicePointsByBranch.put(7, Collections.singletonList(servicePoint));

            gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(
                    new com.qsystems.meddoctorassignment.domain.model.VisitSummary(5001L, 900, "WAITING", "A-005")
            ));
            gateways.visitDetailsById.put(5001L, new com.qsystems.meddoctorassignment.domain.model.VisitDetails(
                    5001L, 900, Collections.singletonList(new com.qsystems.meddoctorassignment.domain.model.VisitUnservedService(301, null, 1))
            ));
            gateways.visitById.put(5001L, new com.qsystems.meddoctorassignment.domain.model.VisitSummary(5001L, 900, "WAITING", "A-005"));

            updateService.refreshConfiguredBranches();
        }
    }
}
