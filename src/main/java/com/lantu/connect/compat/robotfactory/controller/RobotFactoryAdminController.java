package com.lantu.connect.compat.robotfactory.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryAutoSyncRequest;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryAvailableResourceItem;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryCorpMappingUpsertRequest;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryProjectionCreateRequest;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryProjectionListItem;
import com.lantu.connect.compat.robotfactory.dto.RobotFactoryProjectionUpdateRequest;
import com.lantu.connect.compat.robotfactory.dto.RobotFactorySettingsDTO;
import com.lantu.connect.compat.robotfactory.dto.RobotFactorySettingsHealthDTO;
import com.lantu.connect.compat.robotfactory.dto.RobotFactorySettingsUpsertRequest;
import com.lantu.connect.compat.robotfactory.entity.RobotFactoryCorpMapping;
import com.lantu.connect.compat.robotfactory.entity.RobotFactoryProjection;
import com.lantu.connect.compat.robotfactory.entity.RobotFactorySyncLog;
import com.lantu.connect.compat.robotfactory.service.RobotFactoryProjectionService;
import com.lantu.connect.compat.robotfactory.service.RobotFactorySettingsService;
import com.lantu.connect.compat.robotfactory.service.RobotFactorySyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system-config/robot-factory")
@RequireRole({"platform_admin"})
@RequiredArgsConstructor
public class RobotFactoryAdminController {

    private final RobotFactoryProjectionService projectionService;
    private final RobotFactorySyncService syncService;
    private final RobotFactorySettingsService settingsService;

    @GetMapping("/settings")
    public R<RobotFactorySettingsDTO> getSettings() {
        return R.ok(settingsService.getSettings());
    }

    @PutMapping("/settings")
    public R<RobotFactorySettingsDTO> saveSettings(@RequestBody RobotFactorySettingsUpsertRequest request) {
        return R.ok(settingsService.saveSettings(request));
    }

    @PostMapping("/settings/test-connection")
    public R<RobotFactorySettingsHealthDTO> testConnection(@RequestBody(required = false) RobotFactorySettingsUpsertRequest request) {
        return R.ok(settingsService.testConnection(request));
    }

    @GetMapping("/settings/health")
    public R<RobotFactorySettingsHealthDTO> getHealthStatus() {
        return R.ok(settingsService.getHealthStatus());
    }

    @GetMapping("/corp-mappings")
    public R<List<RobotFactoryCorpMapping>> listCorpMappings() {
        return R.ok(projectionService.listCorpMappings());
    }

    @PostMapping("/corp-mappings")
    public R<RobotFactoryCorpMapping> createCorpMapping(@Valid @RequestBody RobotFactoryCorpMappingUpsertRequest request) {
        return R.ok(projectionService.createCorpMapping(request));
    }

    @PutMapping("/corp-mappings/{id}")
    public R<RobotFactoryCorpMapping> updateCorpMapping(@PathVariable Long id,
                                                        @Valid @RequestBody RobotFactoryCorpMappingUpsertRequest request) {
        return R.ok(projectionService.updateCorpMapping(id, request));
    }

    @GetMapping("/available-resources")
    public R<List<RobotFactoryAvailableResourceItem>> listAvailableResources(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(projectionService.listAvailablePublishedResources(keyword, limit));
    }

    @GetMapping("/projections")
    public R<PageResult<RobotFactoryProjectionListItem>> pageProjections(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String syncStatus,
            @RequestParam(required = false) Boolean autoSyncEnabled,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String keyword) {
        return R.ok(projectionService.pageProjections(page, pageSize, syncStatus, autoSyncEnabled, schoolId, keyword));
    }

    @GetMapping("/projections/{id}")
    public R<RobotFactoryProjectionListItem> getProjection(@PathVariable Long id) {
        return R.ok(projectionService.getProjection(id));
    }

    @PostMapping("/projections")
    public R<RobotFactoryProjectionListItem> createProjection(@Valid @RequestBody RobotFactoryProjectionCreateRequest request) {
        return R.ok(projectionService.createProjection(request));
    }

    @PutMapping("/projections/{id}")
    public R<RobotFactoryProjectionListItem> updateProjection(@PathVariable Long id,
                                                              @RequestBody RobotFactoryProjectionUpdateRequest request) {
        return R.ok(projectionService.updateProjection(id, request));
    }

    @PostMapping("/projections/{id}/sync")
    public R<RobotFactoryProjection> syncProjection(@PathVariable Long id) {
        return R.ok(syncService.manualSync(id));
    }

    @DeleteMapping("/projections/{id}/external")
    public R<RobotFactoryProjection> deleteExternal(@PathVariable Long id) {
        return R.ok(syncService.deleteExternal(id, "delete"));
    }

    @PostMapping("/projections/{id}/auto-sync")
    public R<RobotFactoryProjection> setAutoSync(@PathVariable Long id,
                                                 @Valid @RequestBody RobotFactoryAutoSyncRequest request) {
        return R.ok(projectionService.setAutoSync(id, Boolean.TRUE.equals(request.getEnabled())));
    }

    @GetMapping("/sync-logs")
    public R<PageResult<RobotFactorySyncLog>> pageSyncLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long projectionId,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String action) {
        return R.ok(projectionService.pageSyncLogs(page, pageSize, projectionId, success, action));
    }
}
