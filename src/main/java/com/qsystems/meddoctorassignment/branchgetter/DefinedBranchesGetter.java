package com.qsystems.meddoctorassignment.branchgetter;

import com.qsystems.meddoctorassignment.config.OrchestraProperties;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Стратегия чтения фиксированного списка отделений из конфигурации.
 */
@Singleton
public class DefinedBranchesGetter implements BranchGettingStrategy {

    private final OrchestraProperties orchestraProperties;

    public DefinedBranchesGetter(OrchestraProperties orchestraProperties) {
        this.orchestraProperties = orchestraProperties;
    }

    @Override
    public boolean supports(String branchesForCache) {
        return branchesForCache != null && !branchesForCache.trim().isEmpty() && !"*".equals(branchesForCache.trim());
    }

    @Override
    public Collection<Integer> getBranchIds() {
        String raw = orchestraProperties.getBranchesForCache();
        List<Integer> result = new ArrayList<Integer>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        String[] tokens = raw.split(",");
        for (String token : tokens) {
            String value = token.trim();
            if (!value.isEmpty()) {
                result.add(Integer.parseInt(value));
            }
        }
        return result;
    }
}
