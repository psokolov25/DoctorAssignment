package com.qsystems.meddoctorassignment.adapter.gateway;

import com.qsystems.meddoctorassignment.adapter.medrobot.dto.MedRobotOptimalServiceResponse;
import java.util.Set;

/** REST-шлюз к med-robot для выбора оптимальной услуги и очереди. */
public interface MedRobotOptimalServiceGateway {

  /**
   * Запрашивает у med-robot оптимальную пару serviceId/queueId.
   *
   * @param branchId идентификатор отделения
   * @param currentServiceId текущая или предварительно выбранная услуга, передаваемая в path
   * @param unservedServiceIds полный набор непройденных услуг визита
   * @param ticketNumber номер талона для plain text режима
   * @return ответ med-robot
   */
  MedRobotOptimalServiceResponse selectOptimalService(
      int branchId, int currentServiceId, Set<Integer> unservedServiceIds, String ticketNumber);
}
