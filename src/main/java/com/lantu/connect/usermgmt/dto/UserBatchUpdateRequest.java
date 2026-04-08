package com.lantu.connect.usermgmt.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * POST /user-mgmt/users/batch：与 {@link UpdateUserRequest} 并列字段 + ids。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserBatchUpdateRequest extends UpdateUserRequest {

    @NotEmpty
    @Size(max = 200)
    private List<Long> ids;
}
