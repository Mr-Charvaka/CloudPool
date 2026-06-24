package com.cloudpool.model.enums;

public interface SoftDeletable {
    boolean isDeleted();
    void softDelete();
    void restore();
}