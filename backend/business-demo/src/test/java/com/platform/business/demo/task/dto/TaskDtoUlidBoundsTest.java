package com.platform.business.demo.task.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ULID reference fields must be capped at the width of the column they land in.
 *
 * <p>{@code TaskService.create} / {@code update} write {@code deptId} and
 * {@code assigneeUserId} straight onto the entity with no existence lookup, and
 * both {@code demo_task} columns are {@code character(26)}. Verified against the
 * real DB that a 27-char value is rejected ("value too long for type
 * character(26)"), so the request died with an opaque 500 rather than a field
 * error. {@code deptId} was capped at 64 (letting 27–64 through) and
 * {@code assigneeUserId} had no cap at all.
 *
 * <p>Contrast with menu / dept {@code parentId}: those go through a
 * {@code require(...)} lookup first, so garbage there 404s cleanly and needs no
 * length annotation.
 */
class TaskDtoUlidBoundsTest {

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

    private static TaskDto.CreateRequest create(String deptId, String assignee) {
        return new TaskDto.CreateRequest(deptId, "title", null, 1, 1, assignee, LocalDate.now());
    }

    @Test
    void create_rejectsOverlongDeptIdWithAFieldErrorNotA500() {
        var violations = validator.validate(create("D".repeat(27), ULID));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("deptId");
    }

    @Test
    void create_rejectsOverlongAssigneeUserId() {
        var violations = validator.validate(create(ULID, "A".repeat(27)));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("assigneeUserId");
    }

    @Test
    void create_acceptsRealUlids() {
        assertThat(validator.validate(create(ULID, ULID))).isEmpty();
    }

    @Test
    void create_stillAcceptsAnAbsentAssignee() {
        // Optional field — capping the length must not make it required.
        assertThat(validator.validate(create(ULID, null))).isEmpty();
    }

    @Test
    void update_rejectsBothOverlongReferences() {
        var violations = validator.validate(new TaskDto.UpdateRequest(
                "D".repeat(30), "title", null, 1, 1, "A".repeat(30), null));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("deptId", "assigneeUserId");
    }

    @Test
    void update_acceptsAnAllNullPatch() {
        assertThat(validator.validate(new TaskDto.UpdateRequest(
                null, null, null, null, null, null, null))).isEmpty();
    }
}
