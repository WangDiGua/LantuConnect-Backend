package com.lantu.connect.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillPackChunkStatusResponse {

    private int totalChunks;
    private long fileSize;
    private int receivedCount;
    /** 已收到分片下标，升序，用于前端断点续传 */
    private List<Integer> receivedChunkIndices;
}
