package com.qsystems.meddoctorassignment.adapter.gateway;

import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import java.util.Set;

/** REST-шлюз к med-robot для выбора оптимальной услуги и очереди. */
public interface MedRobotOptimalServiceGateway {

  /**
   * Запрашивает у med-robot оптимальную пару serviceId/queueId через JSON-массив услуг.
   *
   * @param branchId идентификатор отделения
   * @param currentServiceId текущая или предварительно выбранная услуга, передаваемая в path
   * @param unservedServiceIds полный набор непройденных услуг визита
   * @return ответ med-robot
   */
  MedRobotOptimalServiceResponse selectOptimalService(
      int branchId, int currentServiceId, Set<Integer> unservedServiceIds);

  /**
   * Запрашивает у med-robot оптимальную пару serviceId/queueId через text/plain body.
   *
   * @param branchId идентификатор отделения
   * @param currentServiceId текущая или предварительно выбранная услуга, передаваемая в path
   * @param plainTextBody строковый идентификатор для второй REST-точки med-robot; в этой службе
   *     используется номер талона
   * @param policy query-параметр policy второй REST-точки med-robot
   * @return ответ med-robot
   */
  MedRobotOptimalServiceResponse selectOptimalServicePlainText(
      int branchId, int currentServiceId, String plainTextBody, String policy);
}
