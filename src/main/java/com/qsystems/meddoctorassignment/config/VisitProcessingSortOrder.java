package com.qsystems.meddoctorassignment.config;

/**
 * Порядок обработки визитов, прочитанных из очереди "врач не назначен".
 */
public enum VisitProcessingSortOrder {
    /** Не менять порядок, в котором визиты вернул endpoint Orchestra. */
    AS_RETURNED,

    /** Сначала самые старые визиты: большее waitingTime, затем меньший visitId. */
    OLDEST_FIRST,

    /** Сначала самые новые визиты: меньшее waitingTime, затем больший visitId. */
    NEWEST_FIRST,

    /** Сначала меньшие идентификаторы визитов. */
    ID_ASC,

    /** Сначала большие идентификаторы визитов. */
    ID_DESC
}
