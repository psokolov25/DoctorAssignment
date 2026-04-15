package com.qsystems.meddoctorassignment.domain.service.impl;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitUnservedService;
import com.qsystems.meddoctorassignment.domain.service.DoctorServiceMatcher;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Singleton
public class DefaultDoctorServiceMatcher implements DoctorServiceMatcher {

    private final AssignmentProperties assignmentProperties;

    public DefaultDoctorServiceMatcher(AssignmentProperties assignmentProperties) {
        this.assignmentProperties = assignmentProperties;
    }

    @Override
    public Optional<SelectedDoctorService> match(VisitDetails visitDetails,
                                                 Set<Integer> doctorAvailableServices,
                                                 BranchAssignmentCache branchCache) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (VisitUnservedService unservedService : visitDetails.getUnservedServices()) {
            Integer resolvedServiceId = resolveServiceId(unservedService, branchCache);
            if (resolvedServiceId == null || !doctorAvailableServices.contains(resolvedServiceId)) {
                continue;
            }
            Integer targetQueueId = branchCache.getServiceIdToQueueId().get(resolvedServiceId);
            if (targetQueueId == null) {
                continue;
            }
            int priority = resolvePriority(unservedService, resolvedServiceId.intValue());
            candidates.add(new Candidate(resolvedServiceId.intValue(), targetQueueId.intValue(), unservedService.getRouteOrder(), priority));
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        candidates.sort(Comparator
                .comparingInt((Candidate candidate) -> candidate.routeOrder != null ? candidate.routeOrder.intValue() : Integer.MAX_VALUE)
                .thenComparingInt(candidate -> candidate.priority)
                .thenComparingInt(candidate -> candidate.serviceId));

        Candidate selected = candidates.get(0);
        return Optional.of(new SelectedDoctorService(selected.serviceId, selected.targetQueueId, selected.routeOrder, selected.describeReason()));
    }

    private Integer resolveServiceId(VisitUnservedService unservedService, BranchAssignmentCache branchCache) {
        if (unservedService.getServiceId() != null) {
            return unservedService.getServiceId();
        }
        if (unservedService.getExternalKey() == null) {
            return null;
        }
        return branchCache.getServiceExternalKeyToId().get(unservedService.getExternalKey());
    }

    private int resolvePriority(VisitUnservedService unservedService, int serviceId) {
        if (unservedService.getExternalKey() != null) {
            return assignmentProperties.resolvePriority(unservedService.getExternalKey(), Integer.MAX_VALUE - 1);
        }
        return assignmentProperties.resolvePriority(String.valueOf(serviceId), Integer.MAX_VALUE - 1);
    }

    private static final class Candidate {
        private final int serviceId;
        private final int targetQueueId;
        private final Integer routeOrder;
        private final int priority;

        private Candidate(int serviceId, int targetQueueId, Integer routeOrder, int priority) {
            this.serviceId = serviceId;
            this.targetQueueId = targetQueueId;
            this.routeOrder = routeOrder;
            this.priority = priority;
        }

        private String describeReason() {
            if (routeOrder != null) {
                return "route-order-" + routeOrder;
            }
            if (priority < Integer.MAX_VALUE - 1) {
                return "configured-priority-" + priority;
            }
            return "service-id-fallback";
        }
    }
}
