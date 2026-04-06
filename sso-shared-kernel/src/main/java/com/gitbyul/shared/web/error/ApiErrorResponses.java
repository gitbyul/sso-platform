package com.gitbyul.shared.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitbyul.shared.exception.ErrorCode;
import com.gitbyul.shared.exception.I18nMessageKeys;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;

public final class ApiErrorResponses {

    private ApiErrorResponses() {
    }

    public static void writeJson(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            MessageSource messageSource,
            Locale locale,
            ErrorCode errorCode,
            int httpStatus)
            throws IOException {
        String messageKey = I18nMessageKeys.error(errorCode);
        String message =
                messageSource.getMessage(messageKey, null, errorCode.code(), locale);
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(), new ApiErrorResponse(errorCode.code(), message));
    }
}
