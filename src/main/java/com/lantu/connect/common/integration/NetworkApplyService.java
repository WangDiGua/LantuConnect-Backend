package com.lantu.connect.common.integration;

/**
 * 网络下发服务接口
 *
 * @author 王帝
 * @date 2026-03-23
 */
public interface NetworkApplyService {

    NetworkApplyResult apply(NetworkApplyRequest request);

    NetworkApplyResult queryStatus(String taskId);
}
