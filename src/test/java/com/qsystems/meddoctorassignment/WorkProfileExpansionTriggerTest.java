package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.branchgetter.AllBranchesGetter;
import com.qsystems.meddoctorassignment.branchgetter.DefinedBranchesGetter;
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
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultLoggedDoctorContextResolver;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultUnknownDoctorQueueVisitProvider;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultVisitAssignmentExecutor;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultVisitRouteAnalyzer;
import com.qsystems.meddoctorassignment.domain.service.impl.MedRobotAwareDoctorServiceSelectionService;
import com.qsystems.meddoctorassignment.event.DoctorAssignmentEventHandler;
import com.qsystems.meddoctorassignment.event.UserSessionReadinessCoordinator;
import com.qsystems.meddoctorassignment.event.WorkProfileExpansionTriggerEvaluator;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.support.InMemoryTestGateways;
import com.qsystems.meddoctorassignment.util.BranchLockManager;
import com.qsystems.meddoctorassignment.util.EventDeduplicator;
import com.qsystems.meddoctorassignment.util.ProcessedVisitRegistry;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WorkProfileExpansionTriggerTest {

    @Test
    void rawSetWorkProfileTriggersImmediateAssignmentWhenServicesExpandDuringActiveSession() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopologyForProfileExpansion();

        OrchestraEvent event = fixture.newSetWorkProfileEvent(16, "Расширенный");
        fixture.handler.handle(event);

        Assertions.assertEquals(1, fixture.gateways.assignedOperations.size());
        Assertions.assertEquals(1, fixture.gateways.transferredOperations.size());
        Assertions.assertEquals(16,
                fixture.container.getOrCreateBranchCache(7)
                        .getServicePointRuntimeStateMap()
                        .get(41220000000007L)
                        .getWorkProfileId());
    }

    @Test
    void rawSetWorkProfileDoesNotTriggerImmediateAssignmentWhenServicesDoNotExpand() {
        Fixture fixture = new Fixture();
        fixture.prepareBranchTopologyForProfileExpansion();

        OrchestraEvent event = fixture.newSetWorkProfileEvent(17, "Суженный");
        fixture.handler.handle(event);

        Assertions.assertTrue(fixture.gateways.assignedOperations.isEmpty());
        Assertions.assertTrue(fixture.gateways.transferredOperations.isEmpty());
        Assertions.assertEquals(17,
                fixture.container.getOrCreateBranchCache(7)
                        .getServicePointRuntimeStateMap()
                        .get(41220000000007L)
                        .getWorkProfileId());
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
        final DefaultDoctorAvailableServicesResolver availableServicesResolver = new DefaultDoctorAvailableServicesResolver();
        final AutonomousMedicalExamAssignmentService serviceLogic = new AutonomousMedicalExamAssignmentService(
                updateService,
                container,
                availableServicesResolver,
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

        final DefaultLoggedDoctorContextResolver contextResolver = new DefaultLoggedDoctorContextResolver(container, gateways);
        final DoctorAssignmentEventHandler handler = new DoctorAssignmentEventHandler(
                new EventDeduplicator(),
                contextResolver,
                serviceLogic,
                assignmentProperties,
                new UserSessionReadinessCoordinator(assignmentProperties),
                new WorkProfileExpansionTriggerEvaluator(container, contextResolver, availableServicesResolver)
        );

        void prepareBranchTopologyForProfileExpansion() {
            orchestraProperties.setBranchesForCache("7");
            assignmentProperties.setUnknownDoctorQueueId(900);
            assignmentProperties.setDryRun(false);
            assignmentProperties.setSetWorkProfileTriggerEnabled(false);
            assignmentProperties.setWorkProfileExpandedTriggerEnabled(true);

            com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData s301 = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData();
            s301.setId(301);
            s301.setInternalName("old-service");
            com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData s302 = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData();
            s302.setId(302);
            s302.setInternalName("new-service");
            gateways.servicesByBranch.put(7, Arrays.asList(s301, s302));

            com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue unknown = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue();
            unknown.setId(900);
            unknown.setName("врач не назначен");
            com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue q301 = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue();
            q301.setId(901);
            q301.setName("old-profile-queue");
            com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue q302 = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue();
            q302.setId(902);
            q302.setName("expanded-profile-queue");
            gateways.allQueuesByBranch.put(7, Arrays.asList(unknown, q301, q302));
            gateways.queueByBranchAndService.put("7|301", q301);
            gateways.queueByBranchAndService.put("7|302", q302);

            com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData oldProfile = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData();
            oldProfile.setId(15);
            oldProfile.setName("Базовый");
            com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData expandedProfile = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData();
            expandedProfile.setId(16);
            expandedProfile.setName("Расширенный");
            com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData narrowedProfile = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData();
            narrowedProfile.setId(17);
            narrowedProfile.setName("Суженный");
            gateways.workProfilesByBranch.put(7, Arrays.asList(oldProfile, expandedProfile, narrowedProfile));
            gateways.workProfileQueues.put("7|15", Collections.singletonList(q301));
            gateways.workProfileQueues.put("7|16", Arrays.asList(q301, q302));
            gateways.workProfileQueues.put("7|17", Collections.singletonList(q301));

            com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData servicePoint = new com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData();
            servicePoint.setId(41220000000007L);
            servicePoint.setBranchId(7);
            servicePoint.setStaffId(45);
            servicePoint.setWorkProfileId(15);
            servicePoint.setStatus("OPEN");
            servicePoint.setName("Кабинет 7");
            gateways.servicePointsByBranch.put(7, Collections.singletonList(servicePoint));

            gateways.waitingVisitsByQueue.put("7|900", Collections.singletonList(
                    new VisitSummary(5001L, 900, "WAITING", "A-005")
            ));
            VisitDetails details = new VisitDetails(5001L, 900, Collections.singletonList(new VisitUnservedService(302, null, 1)));
            details.setCurrentServiceId(72);
            gateways.visitDetailsById.put(5001L, details);
            gateways.visitById.put(5001L, new VisitSummary(5001L, 900, "WAITING", "A-005"));

            updateService.refreshConfiguredBranches();
        }

        OrchestraEvent newSetWorkProfileEvent(int workProfileOrigId, String workProfileName) {
            OrchestraEvent event = new OrchestraEvent();
            event.setEventName("SET_WORK_PROFILE");
            event.setUnitId(41220000000007L);
            event.getParameters().put("branchId", 7);
            event.getParameters().put("userId", 45);
            event.getParameters().put("workProfileOrigId", workProfileOrigId);
            event.getParameters().put("workProfileName", workProfileName);
            event.getParameters().put("servicePointId", 41220000000007L);
            event.getParameters().put("servicePointName", "Кабинет 7");
            event.getParameters().put("userName", "doctor-45");
            return event;
        }
    }
}
