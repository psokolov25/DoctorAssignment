package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;

import java.util.Optional;
import java.util.Set;

/** Выбирает наиболее подходящую непройденную услугу визита для текущего врача. */
public interface DoctorServiceMatcher {

  /** Выбирает наиболее подходящую услугу из маршрута визита. */
  Optional<SelectedDoctorService> match(
      VisitDetails visitDetails,
      Set<Integer> doctorAvailableServices,
      BranchAssignmentCache branchCache);
}
