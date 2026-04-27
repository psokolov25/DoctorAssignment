package com.qsystems.meddoctorassignment.config;

/**
 * Формат тела запроса к med-robot для REST-точки выбора оптимальной услуги.
 */
public enum MedRobotRequestBodyMode {
  /**
   * Старый контракт: Content-Type application/json, тело — JSON-массив id непройденных услуг.
   */
  UNSERVED_SERVICE_IDS_JSON_ARRAY,

  /**
   * Новый контракт: Content-Type text/plain, тело — строка номера талона.
   */
  TICKET_NUMBER_PLAIN_TEXT
}
