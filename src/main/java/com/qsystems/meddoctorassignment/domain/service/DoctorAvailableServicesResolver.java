package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import java.util.Set;

/** Вычисляет множество услуг, доступных врачу в рамках текущего рабочего профиля. */
public interface DoctorAvailableServicesResolver {

  /** Вычисляет множество услуг, доступных врачу в его текущем рабочем профиле. */
  Set<Integer> resolve(DoctorContext doctorContext, BranchAssignmentCache branchCache);
}
