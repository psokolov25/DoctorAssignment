package com.qsystems.meddoctorassignment.event;

import com.qsystems.meddoctorassignment.cache.OrchestraDataCacheContainer;
import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.cache.model.ServicePointRuntimeState;
import com.qsystems.meddoctorassignment.domain.service.DoctorAvailableServicesResolver;
import com.qsystems.meddoctorassignment.domain.service.LoggedDoctorContextResolver;
import com.qsystems.meddoctorassignment.model.event.DoctorContext;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import com.qsystems.meddoctorassignment.model.event.TriggerSource;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

/**
 * Определяет, нужно ли запускать повторный assignment cycle при смене рабочего профиля
 * внутри уже активной пользовательской сессии.
 *
 * <p>Trigger должен срабатывать не на любой SET_WORK_PROFILE, а только тогда, когда новый
 * профиль реально расширил множество услуг, доступных врачу. Это позволяет автоматически
 * дочищать очередь «врач не назначен» после расширения полномочий врача, не поднимая лишние
 * циклы на профилях, которые ничего нового не добавили.</p>
 */
@Singleton
public class WorkProfileExpansionTriggerEvaluator {

    private static final Logger log = LoggerFactory.getLogger(WorkProfileExpansionTriggerEvaluator.class);

    private final OrchestraDataCacheContainer cacheContainer;
    private final LoggedDoctorContextResolver loggedDoctorContextResolver;
    private final DoctorAvailableServicesResolver doctorAvailableServicesResolver;

    public WorkProfileExpansionTriggerEvaluator(OrchestraDataCacheContainer cacheContainer,
                                                LoggedDoctorContextResolver loggedDoctorContextResolver,
                                                DoctorAvailableServicesResolver doctorAvailableServicesResolver) {
        this.cacheContainer = cacheContainer;
        this.loggedDoctorContextResolver = loggedDoctorContextResolver;
        this.doctorAvailableServicesResolver = doctorAvailableServicesResolver;
    }

    /**
     * Сравнивает старый и новый профиль активной сессии врача и решает, нужен ли повторный цикл.
     */
    public EvaluationResult evaluate(OrchestraEvent event) {
        DoctorContext newContext;
        try {
            newContext = loggedDoctorContextResolver.resolve(event, TriggerSource.WORK_PROFILE_EXPANDED);
        } catch (Exception exception) {
            log.warn("Could not resolve doctor context for work-profile expansion trigger: {}", exception.getMessage());
            return EvaluationResult.notTriggered("context-unresolved");
        }

        BranchAssignmentCache branchCache = cacheContainer.getOrCreateBranchCache(newContext.getBranchId());
        ServicePointRuntimeState runtimeState = branchCache.getServicePointRuntimeStateMap().get(newContext.getServicePointId());
        if (runtimeState == null) {
            log.info("Skip work-profile expansion trigger because runtime session state is missing branchId={} servicePointId={} staffId={} newWorkProfileId={}",
                    newContext.getBranchId(),
                    newContext.getServicePointId(),
                    newContext.getStaffId(),
                    newContext.getWorkProfileId());
            return EvaluationResult.notTriggered("runtime-state-missing");
        }

        int previousWorkProfileId = runtimeState.getWorkProfileId();
        if (previousWorkProfileId <= 0) {
            runtimeState.setWorkProfileId(newContext.getWorkProfileId());
            runtimeState.touch();
            return EvaluationResult.notTriggered("previous-work-profile-missing");
        }

        if (previousWorkProfileId == newContext.getWorkProfileId()) {
            runtimeState.touch();
            return EvaluationResult.notTriggered("same-work-profile");
        }

        DoctorContext previousContext = new DoctorContext();
        previousContext.setBranchId(newContext.getBranchId());
        previousContext.setServicePointId(newContext.getServicePointId());
        previousContext.setServicePointLogicId(newContext.getServicePointLogicId());
        previousContext.setStaffId(newContext.getStaffId());
        previousContext.setWorkProfileId(previousWorkProfileId);
        previousContext.setTriggerSource(TriggerSource.WORK_PROFILE_EXPANDED);
        previousContext.setServicePointName(newContext.getServicePointName());
        previousContext.setUserName(newContext.getUserName());

        Set<Integer> previousServices = safeResolve(previousContext, branchCache);
        Set<Integer> newServices = safeResolve(newContext, branchCache);
        boolean expanded = newServices.containsAll(previousServices) && newServices.size() > previousServices.size();

        runtimeState.setBranchId(newContext.getBranchId());
        runtimeState.setServicePointId(newContext.getServicePointId());
        runtimeState.setStaffId(newContext.getStaffId());
        runtimeState.setWorkProfileId(newContext.getWorkProfileId());
        runtimeState.setStatus("OPEN");
        runtimeState.touch();

        if (!expanded) {
            log.info("Skip work-profile expansion trigger because available services did not expand branchId={} servicePointId={} staffId={} previousWorkProfileId={} newWorkProfileId={} previousServices={} newServices={}",
                    newContext.getBranchId(),
                    newContext.getServicePointId(),
                    newContext.getStaffId(),
                    previousWorkProfileId,
                    newContext.getWorkProfileId(),
                    previousServices,
                    newServices);
            return EvaluationResult.notTriggered("services-not-expanded");
        }

        log.info("Detected work-profile expansion during active session branchId={} servicePointId={} staffId={} previousWorkProfileId={} newWorkProfileId={} previousServices={} newServices={}",
                newContext.getBranchId(),
                newContext.getServicePointId(),
                newContext.getStaffId(),
                previousWorkProfileId,
                newContext.getWorkProfileId(),
                previousServices,
                newServices);
        return EvaluationResult.triggered(newContext, previousWorkProfileId, previousServices, newServices);
    }

    private Set<Integer> safeResolve(DoctorContext context, BranchAssignmentCache branchCache) {
        Set<Integer> result = doctorAvailableServicesResolver.resolve(context, branchCache);
        return result != null ? result : Collections.<Integer>emptySet();
    }

    /**
     * Результат оценки смены профиля: либо trigger действительно должен запуститься,
     * либо событие было признано недостаточным для повторного цикла и снабжено причиной.
     */
    public static final class EvaluationResult {
        private final boolean triggered;
        private final DoctorContext doctorContext;
        private final int previousWorkProfileId;
        private final Set<Integer> previousServices;
        private final Set<Integer> newServices;
        private final String reason;

        private EvaluationResult(boolean triggered, DoctorContext doctorContext, int previousWorkProfileId, Set<Integer> previousServices, Set<Integer> newServices, String reason) {
            this.triggered = triggered;
            this.doctorContext = doctorContext;
            this.previousWorkProfileId = previousWorkProfileId;
            this.previousServices = previousServices;
            this.newServices = newServices;
            this.reason = reason;
        }

        public static EvaluationResult triggered(DoctorContext doctorContext, int previousWorkProfileId, Set<Integer> previousServices, Set<Integer> newServices) {
            return new EvaluationResult(true, doctorContext, previousWorkProfileId, previousServices, newServices, null);
        }

        public static EvaluationResult notTriggered(String reason) {
            return new EvaluationResult(false, null, 0, Collections.<Integer>emptySet(), Collections.<Integer>emptySet(), reason);
        }

        public boolean isTriggered() { return triggered; }
        public DoctorContext getDoctorContext() { return doctorContext; }
        public int getPreviousWorkProfileId() { return previousWorkProfileId; }
        public Set<Integer> getPreviousServices() { return previousServices; }
        public Set<Integer> getNewServices() { return newServices; }
        public String getReason() { return reason; }
    }
}
