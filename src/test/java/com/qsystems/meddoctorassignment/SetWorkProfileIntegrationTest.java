package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.branchgetter.AllBranchesGetter;
import com.qsystems.meddoctorassignment.branchgetter.DefinedBranchesGetter;
import com.qsystems.meddoctorassignment.cache.BranchCacheUpdater;
import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.service.OrchestraDataCacheUpdateService;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import com.qsystems.meddoctorassignment.domain.service.AutonomousMedicalExamAssignmentService;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultDoctorAvailableServicesResolver;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultDoctorServiceMatcher;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultLoggedDoctorContextResolver;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultUnknownDoctorQueueVisitProvider;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultVisitAssignmentExecutor;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultVisitRouteAnalyzer;
import com.qsystems.meddoctorassignment.event.DoctorAssignmentEventHandler;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.support.InMemoryTestGateways;
import com.qsystems.meddoctorassignment.util.BranchLockManager;
import com.qsystems.meddoctorassignment.util.EventDeduplicator;
import com.qsystems.meddoctorassignment.util.ProcessedVisitRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

public class SetWorkProfileIntegrationTest {

    @Test
    void setWorkProfileEventTriggersReevaluationAndAssignment() {
        InMemoryTestGateways gateways = new InMemoryTestGateways();
        OrchestraDataCacheContainer container = new OrchestraDataCacheContainer();
        AssignmentProperties assignmentProperties = new AssignmentProperties();
        assignmentProperties.setDryRun(false);
        assignmentProperties.setUnknownDoctorQueueId(900);
        OrchestraProperties orchestraProperties = new OrchestraProperties();
        orchestraProperties.setBranchesForCache("7");

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

        BranchCacheUpdater updater = new BranchCacheUpdater(gateways, container, assignmentProperties);
        OrchestraDataCacheUpdateService updateService = new OrchestraDataCacheUpdateService(
                updater,
                container,
                orchestraProperties,
                assignmentProperties,
                new AllBranchesGetter(gateways),
                new DefinedBranchesGetter(orchestraProperties)
        );
        updateService.refreshConfiguredBranches();

        AutonomousMedicalExamAssignmentService serviceLogic = new AutonomousMedicalExamAssignmentService(
                updateService,
                container,
                new DefaultDoctorAvailableServicesResolver(),
                new DefaultUnknownDoctorQueueVisitProvider(gateways),
                new DefaultVisitRouteAnalyzer(gateways),
                new DefaultDoctorServiceMatcher(assignmentProperties),
                new DefaultVisitAssignmentExecutor(gateways, assignmentProperties),
                new BranchLockManager(),
                new ProcessedVisitRegistry(),
                assignmentProperties
        );

        DoctorAssignmentEventHandler handler = new DoctorAssignmentEventHandler(
                new EventDeduplicator(),
                new DefaultLoggedDoctorContextResolver(container, gateways),
                serviceLogic,
                assignmentProperties
        );

        OrchestraEvent event = new OrchestraEvent();
        event.setEventName("SET_WORK_PROFILE");
        event.setUnitId(41220000000007L);
        event.getParameters().put("branchId", 7);
        event.getParameters().put("userId", 45);
        event.getParameters().put("workProfileOrigId", 15);
        event.getParameters().put("servicePointId", 41220000000007L);

        handler.handle(event);

        Assertions.assertEquals(1, gateways.assignedOperations.size());
        Assertions.assertEquals(1, gateways.transferredOperations.size());
    }
}
