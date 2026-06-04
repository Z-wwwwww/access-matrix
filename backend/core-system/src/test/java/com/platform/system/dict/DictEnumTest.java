package com.platform.system.dict;

import com.platform.core.common.dict.CommonStatus;
import com.platform.core.common.dict.DictEnum;
import com.platform.core.common.error.BusinessException;
import com.platform.system.dict.builtin.DataScopeDict;
import com.platform.system.dict.builtin.MenuType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the DictEnum reverse-lookup + validation guard that backs the
 * service-boundary input validation (tier ①). Pure — no Spring / DB.
 */
class DictEnumTest {

    @Test
    @DisplayName("fromCode resolves legal codes and returns null for unknown/null")
    void fromCode_resolves_and_nulls() {
        assertThat(DictEnum.fromCode(CommonStatus.class, 1)).isEqualTo(CommonStatus.ENABLED);
        assertThat(DictEnum.fromCode(CommonStatus.class, 0)).isEqualTo(CommonStatus.DISABLED);
        assertThat(DictEnum.fromCode(CommonStatus.class, 9)).isNull();
        assertThat(DictEnum.fromCode(CommonStatus.class, null)).isNull();
        assertThat(DictEnum.fromCode(MenuType.class, 2)).isEqualTo(MenuType.MENU);
    }

    @Test
    @DisplayName("requireValid passes for a legal code")
    void requireValid_passes_for_legal() {
        assertThatNoException()
                .isThrownBy(() -> DictEnum.requireValid(DataScopeDict.class, 5, "dataScope"));
    }

    @Test
    @DisplayName("requireValid throws BusinessException (→400) for an out-of-range code")
    void requireValid_throws_for_illegal() {
        assertThatThrownBy(() -> DictEnum.requireValid(DataScopeDict.class, 99, "dataScope"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dataScope");
    }

    @Test
    @DisplayName("requireValid rejects null")
    void requireValid_throws_for_null() {
        assertThatThrownBy(() -> DictEnum.requireValid(CommonStatus.class, null, "status"))
                .isInstanceOf(BusinessException.class);
    }
}
