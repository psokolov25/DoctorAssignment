package com.qsystems.meddoctorassignment.adapter.medrobot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Ответ med-robot с выбранной оптимальной услугой и целевой очередью. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedRobotOptimalServiceResponse {

  private Integer serviceId;
  private Integer queueId;

  public Integer getServiceId() {
    return serviceId;
  }

  public void setServiceId(Integer serviceId) {
    this.serviceId = serviceId;
  }

  public Integer getQueueId() {
    return queueId;
  }

  public void setQueueId(Integer queueId) {
    this.queueId = queueId;
  }

  /**
   * Проверяет, вернул ли med-robot реальную пару услуга/очередь.
   *
   * <p>Пара {@code 0/0} трактуется как штатный ответ «нет необслуженных услуг», а не как
   * назначение в очередь с id 0.</p>
   */
  public boolean hasSelectedServiceAndQueue() {
    if (serviceId == null || queueId == null) {
      return false;
    }
    return serviceId.intValue() > 0 && queueId.intValue() > 0;
  }
}
