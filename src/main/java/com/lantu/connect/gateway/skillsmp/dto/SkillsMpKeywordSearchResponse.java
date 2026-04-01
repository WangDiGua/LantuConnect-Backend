package com.lantu.connect.gateway.skillsmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SkillsMpKeywordSearchResponse {

    private Boolean success;
    private Payload data;
    private ErrorBody error;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Payload {
        private List<SkillsMpSkillJson> skills;
        private Pagination pagination;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Pagination {
        private Integer page;
        private Integer limit;
        private Integer total;
        private Integer totalPages;
        private Boolean hasNext;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class ErrorBody {
        private String code;
        private String message;
    }
}
