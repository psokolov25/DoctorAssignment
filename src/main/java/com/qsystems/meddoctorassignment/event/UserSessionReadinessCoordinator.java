package com.qsystems.meddoctorassignment.event;

import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Координирует короткую цепочку посадки врача:
 * USER_SERVICE_POINT_SESSION_START -> SET_WORK_PROFILE.
 *
 * <p>По живым логам Orchestra USER_SERVICE_POINT_SESSION_START надежно сообщает факт начала
 * пользовательской сессии service point и несет фактически выбранный в UI профиль врача.
 * SET_WORK_PROFILE при этом может приходить спустя десятки миллисекунд и отражать уже другой
 * серверный профиль/контекст. Поэтому SET_WORK_PROFILE используется как сигнал стабилизации
 * посадки, но итоговый доменный профиль берется из USER_SERVICE_POINT_SESSION_START.</p>
 */
@Singleton
public class UserSessionReadinessCoordinator {

    private static final Logger log = LoggerFactory.getLogger(UserSessionReadinessCoordinator.class);

    private final ConcurrentMap<String, PendingUserSession> pendingByCorrelationKey = new ConcurrentHashMap<String, PendingUserSession>();
    private final AssignmentProperties assignmentProperties;

    public UserSessionReadinessCoordinator(AssignmentProperties assignmentProperties) {
        this.assignmentProperties = assignmentProperties;
    }

    /**
     * Регистрирует начало пользовательской service point session и сохраняет ее как pending-state.
     *
     * <p>С этого шага сервис еще не запускает assignment cycle: до прихода связанного
     * SET_WORK_PROFILE посадка считается не полностью стабилизированной.</p>
     */
    public void registerSessionStart(OrchestraEvent event) {
        String key = resolveCorrelationKey(event);
        PendingUserSession pending = PendingUserSession.from(event, System.currentTimeMillis());
        pendingByCorrelationKey.put(key, pending);
        log.info("Registered pending user session correlationKey={} branchId={} userId={} servicePointId={} servicePointLogicId={} staffTransactionId={} workProfileOrigId={} workProfileName={}",
                key,
                pending.getBranchId(),
                pending.getUserId(),
                pending.getServicePointId(),
                pending.getServicePointLogicId(),
                pending.getStaffTransactionId(),
                pending.getWorkProfileOrigId(),
                pending.getWorkProfileName());
    }

    /**
     * Пытается сопоставить SET_WORK_PROFILE с ранее сохраненным USER_SERVICE_POINT_SESSION_START.
     * При успехе возвращает описание готовой посадки и удаляет pending state.
     *
     * <p>Важно: итоговый профиль врача для доменного workflow берется из
     * USER_SERVICE_POINT_SESSION_START, а SET_WORK_PROFILE здесь служит маркером завершения
     * корреляции и дополнительным диагностическим срезом server-side контекста.</p>
     */
    public Optional<CompletedUserSession> completeIfReady(OrchestraEvent event) {
        String key = resolveCorrelationKey(event);
        PendingUserSession pending = pendingByCorrelationKey.get(key);
        if (pending == null) {
            log.info("No pending user session found for SET_WORK_PROFILE correlationKey={}", key);
            return Optional.empty();
        }

        long ageMs = System.currentTimeMillis() - pending.getCreatedAtMs();
        if (ageMs > assignmentProperties.getUserSessionSettleWindowMs()) {
            pendingByCorrelationKey.remove(key);
            log.info("Pending user session expired correlationKey={} ageMs={} settleWindowMs={}",
                    key,
                    ageMs,
                    assignmentProperties.getUserSessionSettleWindowMs());
            return Optional.empty();
        }

        PendingUserSession setWorkProfileView = PendingUserSession.from(event, System.currentTimeMillis());
        if (!pending.matches(setWorkProfileView)) {
            pendingByCorrelationKey.remove(key);
            log.warn("Pending user session does not match SET_WORK_PROFILE correlationKey={} pending={} incoming={}",
                    key,
                    pending.describe(),
                    setWorkProfileView.describe());
            return Optional.empty();
        }

        pendingByCorrelationKey.remove(key);
        log.info("Completed pending user session correlationKey={} ageMs={} sessionStartProfile={} sessionStartProfileName={} settledProfile={} settledProfileName={} selectedProfileSource=USER_SERVICE_POINT_SESSION_START servicePointId={} servicePointLogicId={}",
                key,
                ageMs,
                pending.getWorkProfileOrigId(),
                pending.getWorkProfileName(),
                setWorkProfileView.getWorkProfileOrigId(),
                setWorkProfileView.getWorkProfileName(),
                pending.getServicePointId(),
                pending.getServicePointLogicId());
        return Optional.of(new CompletedUserSession(pending, setWorkProfileView, ageMs));
    }

    private String resolveCorrelationKey(OrchestraEvent event) {
        Map<String, Serializable> parameters = event.getParameters();
        Object tx = parameters.get("staffTransactionId");
        if (tx != null) {
            return "staffTx:" + String.valueOf(tx);
        }

        Object branchId = parameters.get("branchId");
        Object userId = parameters.get("userId");
        Object servicePointId = parameters.get("servicePointId") != null ? parameters.get("servicePointId") : event.getUnitId();
        return "fallback:" + String.valueOf(branchId) + "|" + String.valueOf(userId) + "|" + String.valueOf(servicePointId);
    }

    /**
     * Завершенная посадка: событие старта сессии плюс сигнал стабилизации через SET_WORK_PROFILE.
     *
     * <p>Объект хранит оба взгляда на одну и ту же посадку: стартовый пользовательский контекст
     * и последующий server-side settled-view. При этом downstream workflow должен использовать
     * профиль из sessionStart, а settledView полезен для логов, корреляции и разборов инцидентов.</p>
     */
    public static final class CompletedUserSession {
        private final PendingUserSession sessionStart;
        private final PendingUserSession settledView;
        private final long ageMs;

        private CompletedUserSession(PendingUserSession sessionStart,
                                     PendingUserSession settledView,
                                     long ageMs) {
            this.sessionStart = sessionStart;
            this.settledView = settledView;
            this.ageMs = ageMs;
        }

        public PendingUserSession getSessionStart() {
            return sessionStart;
        }

        public PendingUserSession getSettledView() {
            return settledView;
        }

        public long getAgeMs() {
            return ageMs;
        }

        public OrchestraEvent toReadyEvent() {
            return sessionStart.copySourceEvent();
        }
    }

    /**
     * Минимальный нормализованный слепок события посадки, достаточный для корреляции.
     */
    public static final class PendingUserSession {
        private final Integer branchId;
        private final Integer userId;
        private final Long servicePointId;
        private final Integer servicePointLogicId;
        private final String staffTransactionId;
        private final Integer workProfileOrigId;
        private final String workProfileName;
        private final long createdAtMs;
        private final OrchestraEvent sourceEvent;

        private PendingUserSession(Integer branchId,
                                   Integer userId,
                                   Long servicePointId,
                                   Integer servicePointLogicId,
                                   String staffTransactionId,
                                   Integer workProfileOrigId,
                                   String workProfileName,
                                   long createdAtMs,
                                   OrchestraEvent sourceEvent) {
            this.branchId = branchId;
            this.userId = userId;
            this.servicePointId = servicePointId;
            this.servicePointLogicId = servicePointLogicId;
            this.staffTransactionId = staffTransactionId;
            this.workProfileOrigId = workProfileOrigId;
            this.workProfileName = workProfileName;
            this.createdAtMs = createdAtMs;
            this.sourceEvent = sourceEvent;
        }

        public static PendingUserSession from(OrchestraEvent event, long createdAtMs) {
            Map<String, Serializable> parameters = event.getParameters();
            Integer branchId = asInteger(parameters.get("branchId"));
            Integer userId = asInteger(parameters.get("userId"));
            Long servicePointId = asLong(parameters.get("servicePointId"));
            if (servicePointId == null) {
                servicePointId = event.getUnitId();
            }
            Integer servicePointLogicId = asInteger(parameters.get("servicePointLogicId"));
            String staffTransactionId = asString(parameters.get("staffTransactionId"));
            Integer workProfileOrigId = asInteger(parameters.get("workProfileOrigId"));
            String workProfileName = asString(parameters.get("workProfileName"));
            return new PendingUserSession(branchId,
                    userId,
                    servicePointId,
                    servicePointLogicId,
                    staffTransactionId,
                    workProfileOrigId,
                    workProfileName,
                    createdAtMs,
                    copyEvent(event));
        }

        public boolean matches(PendingUserSession other) {
            return equalsNullable(branchId, other.branchId)
                    && equalsNullable(userId, other.userId)
                    && equalsNullable(servicePointId, other.servicePointId)
                    && equalsNullable(servicePointLogicId, other.servicePointLogicId)
                    && equalsNullable(staffTransactionId, other.staffTransactionId);
        }

        public OrchestraEvent copySourceEvent() {
            return copyEvent(sourceEvent);
        }

        private static OrchestraEvent copyEvent(OrchestraEvent event) {
            OrchestraEvent copy = new OrchestraEvent();
            copy.setEventName(event.getEventName());
            copy.setEventTime(event.getEventTime());
            copy.setEventType(event.getEventType());
            copy.setUnitId(event.getUnitId());
            Map<String, Serializable> paramsCopy = new HashMap<String, Serializable>();
            if (event.getParameters() != null) {
                paramsCopy.putAll(event.getParameters());
            }
            copy.setParameters(paramsCopy);
            return copy;
        }

        private static boolean equalsNullable(Object left, Object right) {
            if (left == null && right == null) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            return left.equals(right);
        }

        private static Integer asInteger(Object value) {
            if (value == null) {
                return null;
            }
            return Integer.valueOf(String.valueOf(value));
        }

        private static Long asLong(Object value) {
            if (value == null) {
                return null;
            }
            return Long.valueOf(String.valueOf(value));
        }

        private static String asString(Object value) {
            return value != null ? String.valueOf(value) : null;
        }

        public Integer getBranchId() { return branchId; }
        public Integer getUserId() { return userId; }
        public Long getServicePointId() { return servicePointId; }
        public Integer getServicePointLogicId() { return servicePointLogicId; }
        public String getStaffTransactionId() { return staffTransactionId; }
        public Integer getWorkProfileOrigId() { return workProfileOrigId; }
        public String getWorkProfileName() { return workProfileName; }
        public long getCreatedAtMs() { return createdAtMs; }

        public String describe() {
            return "branchId=" + branchId
                    + ",userId=" + userId
                    + ",servicePointId=" + servicePointId
                    + ",servicePointLogicId=" + servicePointLogicId
                    + ",staffTransactionId=" + staffTransactionId
                    + ",workProfileOrigId=" + workProfileOrigId
                    + ",workProfileName=" + workProfileName;
        }
    }
}
