package com.qsystems.meddoctorassignment.config;

/**
 * Порядок обработки визитов, полученных из очереди "врач не назначен".
 */
public enum VisitProcessingSortOrder {
    /**
     * Не менять порядок, который вернул REST endpoint Orchestra.
     */
    AS_RETURNED,

    /**
     * Сначала самые старые визиты: максимальное waitingTime, затем минимальный visit id.
     */
    OLDEST_FIRST,

    /**
     * Сначала самые новые визиты: минимальное waitingTime, затем максимальный visit id.
     */
    NEWEST_FIRST,

    /**
     * Сортировать по возрастанию visit id.
     */
    ID_ASC,

    /**
     * Сортировать по убыванию visit id.
     */
    ID_DESC
}
