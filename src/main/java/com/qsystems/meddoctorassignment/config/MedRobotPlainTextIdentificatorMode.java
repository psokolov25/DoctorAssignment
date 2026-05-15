package com.qsystems.meddoctorassignment.config;

/**
 * Режим идентификатора, передаваемого в text/plain тело запроса med-robot.
 */
public enum MedRobotPlainTextIdentificatorMode {
  /** Номер талона визита. */
  TICKET_NUMBER,

  /** JSON-строка с телом визита. */
  VISIT_JSON,

  /** JSON-строка с объектом VisitDetails текущего визита. */
  VISIT_DETAILS_JSON_OBJECT
}
