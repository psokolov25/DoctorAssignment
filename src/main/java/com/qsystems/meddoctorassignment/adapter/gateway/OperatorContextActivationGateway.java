package com.qsystems.meddoctorassignment.adapter.gateway;

import com.qsystems.meddoctorassignment.model.event.DoctorContext;

/**
 * Абстракция над предварительной активацией серверного operator/service-point context.
 *
 * <p>Нужна для тех инсталляций Orchestra, где простого REST-вызова assign/transfer недостаточно, и
 * перед мутациями требуется отдельный шаг старта/привязки сессии рабочего места.
 */
public interface OperatorContextActivationGateway {

  /**
   * Выполняет или пропускает activation-step для указанного врача.
   *
   * <p>Если activation в конфигурации отключен, реализация должна завершиться без ошибки.
   */
  void activate(DoctorContext doctorContext);
}
