package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import java.util.Optional;
import java.util.Set;

/** Выбирает итоговую услугу и очередь для перевода визита. */
public interface DoctorServiceSelectionService {

  /**
   * Выбирает итоговую пару serviceId/queueId с учетом локального алгоритма и опционального med-robot.
   */
  Optional<SelectedDoctorService> select(
      VisitDetails visitDetails,
      Set<Integer> doctorAvailableServices,
      BranchAssignmentCache branchCache);
}
