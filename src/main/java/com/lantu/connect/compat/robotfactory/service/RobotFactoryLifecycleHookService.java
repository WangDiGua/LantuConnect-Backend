package com.lantu.connect.compat.robotfactory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RobotFactoryLifecycleHookService {

    private final RobotFactorySyncService robotFactorySyncService;

    public void onResourcePublished(Long resourceId) {
        robotFactorySyncService.onResourcePublished(resourceId);
    }

    public void onResourceUpdated(Long resourceId) {
        robotFactorySyncService.onResourceUpdated(resourceId);
    }

    public void onResourceDeprecated(Long resourceId) {
        robotFactorySyncService.onResourceDeprecated(resourceId);
    }
}
