package com.qsystems.meddoctorassignment.adapter.gateway;

import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;

import java.util.Optional;

/**
 * Источник дополнительного контекста по точке обслуживания.
 *
 * <p>Используется как fallback-механизм, когда событие Orchestra не содержит полный набор
 * полей для однозначного определения врача, точки обслуживания или рабочего профиля.</p>
 */
public interface ServicePointContextGateway {

    /**
     * Ищет контекст конкретной точки обслуживания.
     */
    Optional<ServicePointRuntimeState> getServicePointContext(int branchId, long servicePointId);

    /**
     * Ищет точку обслуживания, на которой сейчас находится сотрудник.
     */
    Optional<ServicePointRuntimeState> findByStaff(int branchId, int staffId);
}
