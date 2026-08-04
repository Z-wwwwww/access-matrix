package com.platform.system.rbac.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code deptId} must be capped at {@code core_auth_user.dept_id}'s width.
 *
 * <p>All three write paths set it straight onto the entity with no existence
 * lookup — {@code UserAdminService.create}, {@code update} and
 * {@code changeDept} — and the column is {@code character(26)}. Verified against
 * the real DB that a 27-char value is rejected ("value too long for type
 * character(26)"), so an over-long deptId produced an opaque 500. On the create
 * path it is worse than a wasted request: Keycloak is provisioned BEFORE the
 * insert, so the failure has to be unwound by the KC compensation added for the
 * orphan-user fix.
 */
class UserDtoUlidBoundsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static final String ULID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";   // 26 chars

    @Test
    void createRequest_rejectsOverlongDeptId() {
        var violations = validator.validate(new UserDto.CreateRequest(
                "alice", null, "alice@example.com", "Alice",
                "D".repeat(27), 1, UserDto.ProvisionMode.INVITE));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("deptId");
    }

    @Test
    void createRequest_acceptsARealUlidAndAnAbsentDept() {
        assertThat(validator.validate(new UserDto.CreateRequest(
                "alice", null, "alice@example.com", "Alice",
                ULID, 1, UserDto.ProvisionMode.INVITE))).isEmpty();
        assertThat(validator.validate(new UserDto.CreateRequest(
                "alice", null, "alice@example.com", "Alice",
                null, 1, UserDto.ProvisionMode.INVITE))).isEmpty();
    }

    @Test
    void updateRequest_rejectsOverlongDeptId() {
        var violations = validator.validate(new UserDto.UpdateRequest(
                "alice@example.com", "Alice", "D".repeat(40), 1));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("deptId");
    }

    @Test
    void changeDeptRequest_rejectsOverlongDeptId_andAcceptsBlankForClearing() {
        assertThat(validator.validate(new UserDto.ChangeDeptRequest("D".repeat(27))))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("deptId");
        // Clearing the dept (null / empty) must stay allowed.
        assertThat(validator.validate(new UserDto.ChangeDeptRequest(null))).isEmpty();
        assertThat(validator.validate(new UserDto.ChangeDeptRequest(""))).isEmpty();
        assertThat(validator.validate(new UserDto.ChangeDeptRequest(ULID))).isEmpty();
    }
}
