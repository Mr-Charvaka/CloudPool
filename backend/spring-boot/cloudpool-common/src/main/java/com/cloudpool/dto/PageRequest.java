package com.cloudpool.dto;

public class PageRequest {
    private int page = 0;
    private int size = 20;
    private int maxSize = 100;

    public PageRequest() {}

    public PageRequest(int page, int size) {
        this.page = Math.max(0, page);
        this.size = Math.min(Math.max(1, size), maxSize);
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(0, page); }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = Math.min(Math.max(1, size), maxSize); }
    public int getOffset() { return page * size; }
    public int getMaxSize() { return maxSize; }
    public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
}