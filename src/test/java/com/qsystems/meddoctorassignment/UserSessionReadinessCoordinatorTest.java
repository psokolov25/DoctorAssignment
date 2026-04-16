package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.event.UserSessionReadinessCoordinator;
import com.qsystems.meddoctorassignment.model.event.OrchestraEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UserSessionReadinessCoordinatorTest {

    @Test
    void keepsProfileFromUserServicePointSessionStartAndUsesSetWorkProfileOnlyAsReadinessSignal() {
        AssignmentProperties properties = new AssignmentProperties();
        UserSessionReadinessCoordinator coordinator = new UserSessionReadinessCoordinator(properties);

        OrchestraEvent sessionStart = new OrchestraEvent();
        sessionStart.setEventName("USER_SERVICE_POINT_SESSION_START");
        sessionStart.setUnitId(120000000006L);
        sessionStart.getParameters().put("branchId", 6);
        sessionStart.getParameters().put("userId", 1);
        sessionStart.getParameters().put("servicePointId", 120000000006L);
        sessionStart.getParameters().put("servicePointLogicId", 1);
        sessionStart.getParameters().put("staffTransactionId", 1776327218753843L);
        sessionStart.getParameters().put("workProfileOrigId", 14);
        sessionStart.getParameters().put("workProfileName", "test05");

        OrchestraEvent setWorkProfile = new OrchestraEvent();
        setWorkProfile.setEventName("SET_WORK_PROFILE");
        setWorkProfile.setUnitId(120000000006L);
        setWorkProfile.getParameters().put("branchId", 6);
        setWorkProfile.getParameters().put("userId", 1);
        setWorkProfile.getParameters().put("servicePointId", 120000000006L);
        setWorkProfile.getParameters().put("servicePointLogicId", 1);
        setWorkProfile.getParameters().put("staffTransactionId", 1776327218753843L);
        setWorkProfile.getParameters().put("workProfileOrigId", 7);
        setWorkProfile.getParameters().put("workProfileName", "Универсал");

        coordinator.registerSessionStart(sessionStart);
        UserSessionReadinessCoordinator.CompletedUserSession completed = coordinator.completeIfReady(setWorkProfile).orElse(null);

        Assertions.assertNotNull(completed);
        OrchestraEvent readyEvent = completed.toReadyEvent();
        Assertions.assertEquals(Integer.valueOf(14), readyEvent.getParameters().get("workProfileOrigId"));
        Assertions.assertEquals("test05", readyEvent.getParameters().get("workProfileName"));
        Assertions.assertEquals(Integer.valueOf(7), completed.getSettledView().getWorkProfileOrigId());
        Assertions.assertEquals("Универсал", completed.getSettledView().getWorkProfileName());
    }
}
