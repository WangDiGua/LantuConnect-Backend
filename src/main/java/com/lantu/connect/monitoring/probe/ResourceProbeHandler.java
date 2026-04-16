package com.lantu.connect.monitoring.probe;

public interface ResourceProbeHandler {

    boolean supports(String resourceType);

    ResourceProbeResult probe(ResourceProbeTarget target);
}
