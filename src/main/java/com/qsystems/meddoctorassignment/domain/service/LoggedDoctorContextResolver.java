package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;

/**
 * Восстанавливает доменный контекст врача из события Orchestra.
 */
public interface LoggedDoctorContextResolver {

    /**
     * Нормализует событие Orchestra в доменный контекст врача.
     */
    DoctorContext resolve(OrchestraEvent event, TriggerSource triggerSource);
}
