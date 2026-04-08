package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * POST /system-config/announcements/batch：公告 patch + ids。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnouncementBatchUpdateRequest extends AnnouncementUpdateRequest {

    @NotEmpty
    @Size(max = 200)
    private List<Long> ids;
}
