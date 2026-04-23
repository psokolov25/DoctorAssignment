package com.qsystems.meddoctorassignment.domain.service.impl;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.domain.service.DoctorAvailableServicesResolver;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import jakarta.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Стандартная реализация вычисления доступных врачу услуг через связку
 * {@code workProfile -> queues -> services}.
 */
@Singleton
public class DefaultDoctorAvailableServicesResolver implements DoctorAvailableServicesResolver {

    @Override
    public Set<Integer> resolve(DoctorContext doctorContext, BranchAssignmentCache branchCache) {
        Set<Integer> queueIds = branchCache.getWorkProfileToQueueIds().get(doctorContext.getWorkProfileId());
        if (queueIds == null || queueIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> result = new HashSet<Integer>();
        for (Integer queueId : queueIds) {
            Set<Integer> serviceIds = branchCache.getQueueIdToServiceIds().get(queueId);
            if (serviceIds != null) {
                result.addAll(serviceIds);
            }
        }
        return result;
    }
}
