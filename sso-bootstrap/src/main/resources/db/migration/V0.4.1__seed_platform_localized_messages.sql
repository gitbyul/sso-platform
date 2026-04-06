-- 플랫폼 기본 번역 (tenant_id NULL). 키 규약: error.{ErrorCode.code()}
INSERT INTO shared.localized_messages (message_key, locale, message_text, tenant_id)
SELECT v.message_key, v.locale, v.message_text, NULL
FROM (VALUES
    ('error.TENANT_NOT_IDENTIFIED', 'ko', '테넌트를 식별할 수 없습니다.'),
    ('error.TENANT_NOT_IDENTIFIED', 'en', 'The tenant could not be identified.'),
    ('error.SECURITY_TENANT_MISMATCH', 'ko', '테넌트가 일치하지 않습니다.'),
    ('error.SECURITY_TENANT_MISMATCH', 'en', 'The tenant does not match the security context.'),
    ('error.INVALID_ARGUMENT', 'ko', '요청 값이 올바르지 않습니다.'),
    ('error.INVALID_ARGUMENT', 'en', 'The request value is not valid.'),
    ('error.NOT_FOUND', 'ko', '리소스를 찾을 수 없습니다.'),
    ('error.NOT_FOUND', 'en', 'The resource was not found.'),
    ('error.FORBIDDEN', 'ko', '접근 권한이 없습니다.'),
    ('error.FORBIDDEN', 'en', 'Access is forbidden.'),
    ('error.UNAUTHORIZED', 'ko', '인증이 필요합니다.'),
    ('error.UNAUTHORIZED', 'en', 'Authentication is required.'),
    ('error.INTERNAL_ERROR', 'ko', '서버 처리 중 오류가 발생했습니다.'),
    ('error.INTERNAL_ERROR', 'en', 'An error occurred while processing the request.')
) AS v(message_key, locale, message_text)
WHERE NOT EXISTS (
    SELECT 1
    FROM shared.localized_messages m
    WHERE m.message_key = v.message_key
      AND m.locale = v.locale
      AND m.tenant_id IS NULL
);
