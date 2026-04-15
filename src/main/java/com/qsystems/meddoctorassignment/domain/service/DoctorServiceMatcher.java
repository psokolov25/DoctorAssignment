package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;

import java.util.Optional;
import java.util.Set;

public interface DoctorServiceMatcher {

    Optional<SelectedDoctorService> match(VisitDetails visitDetails, Set<Integer> doctorAvailableServices, BranchAssignmentCache branchCache);
}
