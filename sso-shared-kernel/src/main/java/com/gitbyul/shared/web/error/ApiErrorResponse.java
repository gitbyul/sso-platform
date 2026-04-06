package com.gitbyul.shared.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API 오류 공통 JSON 바디.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String code, String message) {}
