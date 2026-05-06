package com.guidelam.facto.web.dto;

import java.util.ArrayList;
import java.util.List;

public class SupplierDecisionForm {

    private List<MappingDecision> decisions = new ArrayList<>();

    public List<MappingDecision> getDecisions() {
        return decisions;
    }

    public void setDecisions(List<MappingDecision> decisions) {
        this.decisions = decisions;
    }
}
