package com.market.spacecatmarket.application;
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Object id) { super(resource + " '" + id + "' was not found."); }
}
