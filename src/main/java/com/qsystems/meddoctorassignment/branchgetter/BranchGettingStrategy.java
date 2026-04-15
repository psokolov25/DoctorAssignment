package com.qsystems.meddoctorassignment.branchgetter;

import java.util.Collection;

public interface BranchGettingStrategy {

    boolean supports(String branchesForCache);

    Collection<Integer> getBranchIds();
}
