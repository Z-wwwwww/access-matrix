package com.platform.core.infrastructure.web;

import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.config.properties.AppDebugProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A duplicate that slips past a service's pre-check is a CALLER error, not a
 * server bug, and must not surface as a raw 500.
 *
 * <p>Every "create" in this codebase is check-then-insert — {@code
 * RoleAdminService.assertNameUnique}, {@code MenuAdminService.create}'s code
 * probe, {@code DictAdminService.createType} / {@code createItem}, {@code
 * DeptAdminService.create} — so there is a TOCTOU window between the SELECT and
 * the INSERT. The real guard is the unique index, and it does fire: verified
 * against the live database (in a rolled-back transaction) that a second
 * {@code core_rbac_role} row with the same (tenant_id, name) is rejected by
 * {@code uk_core_rbac_role_name}.
 *
 * <p>Spring surfaces that as {@link DuplicateKeyException}. With no handler for
 * it the request fell through to {@code handleGeneric}: HTTP 500, an
 * "Unhandled exception" ERROR in the log, and a tick on the platform
 * dashboard's "API errors (24h)" KPI — which by this project's own rule counts
 * only unexpected server errors, not deliberate rejections. That is the same
 * reasoning already written into the {@code NoResourceFoundException} and
 * {@code KeycloakOperationException} handlers.
 *
 * <p>Scope is deliberately {@code DuplicateKeyException}, NOT its parent
 * {@link DataIntegrityViolationException}: a NOT-NULL or foreign-key violation
 * is a server bug and must keep its loud 500 rather than be dressed up as a
 * business error.
 */
class GlobalExceptionHandlerDuplicateKeyTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new AppDebugProperties(false));

    @Test
    void duplicateKeyBecomesABusinessError_notA500() {
        ResponseEntity<JsonResult<Object>> res =
                handler.handleDuplicateKey(new DuplicateKeyException(
                        "duplicate key value violates unique constraint \"uk_core_rbac_role_name\""));

        assertThat(res.getStatusCode())
                .as("a taken name is a caller error; a 500 would also pollute the error-rate KPI")
                .isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().code()).isEqualTo(ErrorCode.BUSINESS_ERROR.code());
        assertThat(res.getBody().msg())
                .as("message is an i18n KEY the frontend localizes, like the Keycloak handler")
                .isEqualTo("error.common.duplicateKey");
    }

    @Test
    void theRawConstraintNameIsNotEchoedToTheClient() {
        ResponseEntity<JsonResult<Object>> res =
                handler.handleDuplicateKey(new DuplicateKeyException(
                        "duplicate key value violates unique constraint \"uk_core_auth_user_tenant_email\""));

        assertThat(res.getBody().msg()).doesNotContain("uk_core_auth_user");
        assertThat(String.valueOf(res.getBody().data())).doesNotContain("uk_core_auth_user");
    }

    @Test
    void otherIntegrityViolationsAreNotSwallowed() {
        // A NOT NULL / FK violation is a server bug — it must NOT be handled here,
        // so that it keeps falling through to handleGeneric's loud 500.
        boolean handledByDuplicateKeyHandler = java.util.Arrays
                .stream(GlobalExceptionHandler.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("handleDuplicateKey"))
                .anyMatch(m -> m.getParameterTypes()[0].equals(DataIntegrityViolationException.class));

        assertThat(handledByDuplicateKeyHandler)
                .as("must bind to DuplicateKeyException specifically, not its parent")
                .isFalse();
    }
}
