package com.lantu.connect.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillPackChunkInitResponse {

    private String uploadId;
    private int chunkSize;
    private int totalChunks;
    private long fileSize;
}
