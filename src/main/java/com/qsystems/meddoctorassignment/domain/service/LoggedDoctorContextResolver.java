package com.qsystems.meddoctorassignment.domain.service;

import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;

public interface LoggedDoctorContextResolver {

    DoctorContext resolve(OrchestraEvent event, TriggerSource triggerSource);
}
