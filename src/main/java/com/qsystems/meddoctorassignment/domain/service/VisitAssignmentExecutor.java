package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitSummary;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;

/**
 * Выполняет side-effects доменного решения назначения.
 *
 * <p>Интерфейс специально отделен от этапа выбора услуги, чтобы orchestration-слой мог:
 *
 * <ul>
 *   <li>тестировать selection и mutation независимо;</li>
 *   <li>конфигурировать dry-run/recheck-поведение без изменения алгоритма выбора;</li>
 *   <li>централизовать интеграционные проверки результата после transfer.</li>
 * </ul>
 */
public interface VisitAssignmentExecutor {

  /**
   * Выполняет назначение услуги и перевод визита в целевую очередь.
   *
   * @param doctorContext контекст активного врача (branch/staff/servicePoint/workProfile)
   * @param visitSummary краткий снимок визита из unknown-doctor queue
   * @param visitDetails детальный снимок маршрута визита
   * @param selectedDoctorService целевая услуга и очередь, выбранные доменным алгоритмом
   * @param unknownDoctorQueueId queueId исходной очереди "врач не назначен"
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
