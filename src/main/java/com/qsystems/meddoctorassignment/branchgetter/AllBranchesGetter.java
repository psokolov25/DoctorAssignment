package com.qsystems.meddoctorassignment.branchgetter;

import com.qsystems.meddoctorassignment.adapter.gateway.OrchestraMetadataGateway;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Singleton
public class AllBranchesGetter implements BranchGettingStrategy {

    private final OrchestraMetadataGateway metadataGateway;

    public AllBranchesGetter(OrchestraMetadataGateway metadataGateway) {
        this.metadataGateway = metadataGateway;
    }

    @Override
    public boolean supports(String branchesForCache) {
        return branchesForCache == null || branchesForCache.trim().isEmpty() || "*".equals(branchesForCache.trim());
    }

    @Override
    public Collection<Integer> getBranchIds() {
        List<Integer> ids = new ArrayList<Integer>();
        metadataGateway.getAllBranches().forEach(branch -> ids.add(branch.getId()));
        return ids;
    }
}
