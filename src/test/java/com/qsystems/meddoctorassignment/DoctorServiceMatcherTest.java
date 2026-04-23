package com.qsystems.meddoctorassignment;

import com.qsystems.meddoctorassignment.cache.model.BranchAssignmentCache;
import com.qsystems.meddoctorassignment.config.AssignmentProperties;
import com.qsystems.meddoctorassignment.domain.model.SelectedDoctorService;
import com.qsystems.meddoctorassignment.domain.model.VisitDetails;
import com.qsystems.meddoctorassignment.domain.model.VisitUnservedService;
import com.qsystems.meddoctorassignment.domain.service.impl.DefaultDoctorServiceMatcher;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DoctorServiceMatcherTest {

    @Test
    void choosesFirstMatchingServiceByRouteOrder() {
        AssignmentProperties properties = new AssignmentProperties();
        DefaultDoctorServiceMatcher matcher = new DefaultDoctorServiceMatcher(properties);

        BranchAssignmentCache cache = new BranchAssignmentCache(7);
        cache.getServiceIdToQueueId().put(100, 500);
        cache.getServiceIdToQueueId().put(200, 600);

        VisitDetails details = new VisitDetails(1L, 10, Arrays.asList(
                new VisitUnservedService(200, null, 2),
                new VisitUnservedService(100, null, 1)
        ));

        Optional<SelectedDoctorService> result = matcher.match(details, new HashSet<Integer>(Arrays.asList(100, 200)), cache);

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(100, result.get().getServiceId());
        Assertions.assertEquals("route-order-1", result.get().getSelectionReason());
    }

    @Test
    void choosesConfiguredPriorityWhenRouteOrderIsMissing() {
        AssignmentProperties properties = new AssignmentProperties();
        properties.getServicePriorityByKey().put("blood", 1);
        properties.getServicePriorityByKey().put("xray", 5);

        DefaultDoctorServiceMatcher matcher = new DefaultDoctorServiceMatcher(properties);

        BranchAssignmentCache cache = new BranchAssignmentCache(7);
        cache.getServiceIdToQueueId().put(101, 701);
        cache.getServiceIdToQueueId().put(202, 702);
        cache.getServiceExternalKeyToId().put("blood", 101);
        cache.getServiceExternalKeyToId().put("xray", 202);

        VisitDetails details = new VisitDetails(1L, 10, Arrays.asList(
                new VisitUnservedService(null, "xray", null),
                new VisitUnservedService(null, "blood", null)
        ));

        Optional<SelectedDoctorService> result = matcher.match(details, new HashSet<Integer>(Arrays.asList(101, 202)), cache);

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(101, result.get().getServiceId());
        Assertions.assertEquals("configured-priority-1", result.get().getSelectionReason());
    }
}
