package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;

import java.util.Set;

public interface DoctorAvailableServicesResolver {

    Set<Integer> resolve(DoctorContext doctorContext, BranchAssignmentCache branchCache);
}
