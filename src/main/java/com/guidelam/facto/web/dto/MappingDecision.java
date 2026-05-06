package com.guidelam.facto.web.dto;

public class MappingDecision {

    private Long mappingId;
    private String action;
    private String newSupplierName;

    public MappingDecision() {
    }

    public Long getMappingId() {
        return mappingId;
    }

    public void setMappingId(Long mappingId) {
        this.mappingId = mappingId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getNewSupplierName() {
        return newSupplierName;
    }

    public void setNewSupplierName(String newSupplierName) {
        this.newSupplierName = newSupplierName;
    }
}
