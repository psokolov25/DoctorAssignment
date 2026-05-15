package com.qsystems.meddoctorassignment.support;

import com.qsystems.meddoctorassignment.adapter.gateway.MedRobotOptimalServiceGateway;
import com.qsystems.meddoctorassignment.adapter.gateway.OperatorContextActivationGateway;
import com.qsystems.meddoctorassignment.adapter.gateway.OrchestraMetadataGateway;
import com.qsystems.meddoctorassignment.adapter.gateway.ServicePointContextGateway;
import com.qsystems.meddoctorassignment.adapter.gateway.VisitWorkflowGateway;
import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServiceData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.ServicePointData;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.SmallBranch;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.TinyQueue;
import com.qsystems.meddoctorassignment.adapter.orchestra.dto.WorkProfileData;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import com.qsystems.meddoctorassignment.domain.exception.MutationContextException;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryTestGateways implements OrchestraMetadataGateway, ServicePointContextGateway, VisitWorkflowGateway, OperatorContextActivationGateway, MedRobotOptimalServiceGateway {

    public final Map<Integer, List<SmallBranch>> branches = new HashMap<Integer, List<SmallBranch>>();
    public final Map<Integer, List<ServiceData>> servicesByBranch = new HashMap<Integer, List<ServiceData>>();
    public final Map<String, TinyQueue> queueByBranchAndService = new HashMap<String, TinyQueue>();
    public final Map<Integer, List<ServicePointData>> servicePointsByBranch = new HashMap<Integer, List<ServicePointData>>();
    public final Map<Integer, List<WorkProfileData>> workProfilesByBranch = new HashMap<Integer, List<WorkProfileData>>();
    public final Map<String, List<TinyQueue>> workProfileQueues = new HashMap<String, List<TinyQueue>>();
    public final Map<Integer, List<TinyQueue>> allQueuesByBranch = new HashMap<Integer, List<TinyQueue>>();

    public final Map<String, List<VisitSummary>> waitingVisitsByQueue = new LinkedHashMap<String, List<VisitSummary>>();
    public final Map<Long, VisitDetails> visitDetailsById = new HashMap<Long, VisitDetails>();
    public final Map<Long, VisitSummary> visitById = new HashMap<Long, VisitSummary>();
    public final Map<String, MedRobotOptimalServiceResponse> medRobotResponses = new HashMap<String, MedRobotOptimalServiceResponse>();
    public final List<String> medRobotRequests = new ArrayList<String>();
    public final List<MedRobotOptimalServiceResponse> medRobotSequentialResponses = new ArrayList<MedRobotOptimalServiceResponse>();
    public boolean medRobotSequentialByTransferCount;

    /**
     * Журнал чтения очереди. Формат: branchId|queueId.
     */
    public final List<String> waitingVisitRequests = new ArrayList<String>();

    /**
     * Журнал чтения деталей визита. Формат: branchId|visitId.
     */
    public final List<String> visitDetailsRequests = new ArrayList<String>();

    /**
     * Журнал повторного чтения визита для defensive recheck/post-check. Формат: branchId|visitId.
     */
    public final List<String> findVisitRequests = new ArrayList<String>();

    /**
     * Сквозная трасса workflow для автономных тестов без внешних служб.
     */
    public final List<String> workflowOperations = new ArrayList<String>();

    public boolean medRobotFail;

    public final List<String> activationOperations = new ArrayList<String>();
    public final List<String> assignedOperations = new ArrayList<String>();
    public final List<String> transferredOperations = new ArrayList<String>();
    public int allQueuesReadCount;

    public boolean activationEnabled;
    public boolean activationFail;
    public long failVisitId = -1L;
    public long blockedAssignVisitId = -1L;

    @Override
    public List<SmallBranch> getAllBranches() {
        List<SmallBranch> result = new ArrayList<SmallBranch>();
        for (List<SmallBranch> value : branches.values()) {
            result.addAll(value);
        }
        return result;
    }

    @Override
    public List<ServiceData> getServicesFromBranch(int branchId) {
        return getOrEmpty(servicesByBranch.get(branchId));
    }

    @Override
    public TinyQueue getQueueForServiceInBranch(int branchId, int serviceId) {
        return queueByBranchAndService.get(branchId + "|" + serviceId);
    }

    @Override
    public List<ServicePointData> getServicePointsFromBranch(int branchId) {
        return getOrEmpty(servicePointsByBranch.get(branchId));
    }

    @Override
    public List<WorkProfileData> getWorkProfilesFromBranch(int branchId) {
        return getOrEmpty(workProfilesByBranch.get(branchId));
    }

    @Override
    public List<TinyQueue> getQueuesForWorkProfileInBranch(int branchId, int workProfileId) {
        return getOrEmpty(workProfileQueues.get(branchId + "|" + workProfileId));
    }

    @Override
    public List<TinyQueue> getAllQueuesInBranch(int branchId) {
        allQueuesReadCount++;
        return getOrEmpty(allQueuesByBranch.get(branchId));
    }

    @Override
    public Optional<ServicePointRuntimeState> getServicePointContext(int branchId, long servicePointId) {
        for (ServicePointData data : getServicePointsFromBranch(branchId)) {
            if (data.getId() == servicePointId) {
                return Optional.of(new ServicePointRuntimeState(data.getId(), data.getBranchId(), data.getStaffId(), data.getWorkProfileId(), data.getStatus()));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ServicePointRuntimeState> findByStaff(int branchId, int staffId) {
        for (ServicePointData data : getServicePointsFromBranch(branchId)) {
            if (data.getStaffId() == staffId) {
                return Optional.of(new ServicePointRuntimeState(data.getId(), data.getBranchId(), data.getStaffId(), data.getWorkProfileId(), data.getStatus()));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<VisitSummary> getWaitingVisits(int branchId, int queueId) {
        String request = branchId + "|" + queueId;
        waitingVisitRequests.add(request);
        workflowOperations.add("getWaitingVisits|" + request);
        return getOrEmpty(waitingVisitsByQueue.get(request));
    }

    @Override
    public VisitDetails getVisitDetails(int branchId, long visitId) {
        String request = branchId + "|" + visitId;
        visitDetailsRequests.add(request);
        workflowOperations.add("getVisitDetails|" + request);
        if (visitId == failVisitId) {
            throw new IllegalStateException("simulated downstream failure");
        }
        return visitDetailsById.get(visitId);
    }

    @Override
    public void activate(com.qsystems.meddoctorassignment.model.event.DoctorContext doctorContext) {
        if (!activationEnabled) {
            return;
        }
        activationOperations.add(doctorContext.getBranchId() + "|" + doctorContext.getServicePointId() + "|" + doctorContext.getStaffId() + "|" + doctorContext.getWorkProfileId());
        if (activationFail) {
            throw new MutationContextException("simulated activation failure for branch " + doctorContext.getBranchId());
        }
    }

    @Override
    public void assignServiceToVisit(int branchId, long visitId, int serviceId, int staffId, long servicePointId) {
        if (visitId == blockedAssignVisitId) {
            throw new MutationContextException("simulated inactive operator context for visit " + visitId);
        }
        String operation = branchId + "|" + visitId + "|" + serviceId + "|" + staffId + "|" + servicePointId;
        workflowOperations.add("assignServiceToVisit|" + operation);
        assignedOperations.add(operation);
    }

    @Override
    public void transferVisitToQueue(int branchId, long visitId, int sourceQueueId, int targetQueueId) {
        String operation = branchId + "|" + sourceQueueId + "|" + visitId + "|" + targetQueueId;
        workflowOperations.add("transferVisitToQueue|" + operation);
        transferredOperations.add(operation);
        VisitSummary summary = visitById.get(visitId);
        if (summary != null) {
            summary.setQueueId(targetQueueId);
        }
    }

    @Override
    public Optional<VisitSummary> findVisit(int branchId, long visitId) {
        String request = branchId + "|" + visitId;
        findVisitRequests.add(request);
        workflowOperations.add("findVisit|" + request);
        return Optional.ofNullable(visitById.get(visitId));
    }

    @Override
    public MedRobotOptimalServiceResponse selectOptimalService(int branchId, int currentServiceId, java.util.Set<Integer> unservedServiceIds, String ticketNumber, String visitJsonIdentificator) {
        medRobotRequests.add(branchId + "|" + currentServiceId + "|" + new java.util.TreeSet<Integer>(unservedServiceIds));
        if (medRobotFail) {
            throw new IllegalStateException("simulated med-robot failure");
        }
        if (medRobotSequentialByTransferCount && !medRobotSequentialResponses.isEmpty()) {
            int index = transferredOperations.size();
            if (index < 0) {
                index = 0;
            }
            if (index >= medRobotSequentialResponses.size()) {
                index = medRobotSequentialResponses.size() - 1;
            }
            return medRobotSequentialResponses.get(index);
        }
        return medRobotResponses.get(branchId + "|" + currentServiceId);
    }

    private <T> List<T> getOrEmpty(List<T> input) {
        return input != null ? input : Collections.<T>emptyList();
    }
}
