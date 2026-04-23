package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;

/** Выполняет побочные эффекты назначения услуги и перевода визита. */
public interface VisitAssignmentExecutor {

  /**
   * Выполняет назначение услуги и перевод визита в целевую очередь.
   *
   * @return {@code true}, если визит успешно переведен в ожидаемое состояние
   */
  boolean assign(
      DoctorContext doctorContext,
      VisitSummary visitSummary,
      VisitDetails visitDetails,
      SelectedDoctorService selectedDoctorService,
      int unknownDoctorQueueId);
}
