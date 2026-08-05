package com.platform.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.platform.system.dict.mapper.DictItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The dict read cache is keyed by a caller-supplied string.
 *
 * <p>{@code GET /dict/{code}} is authenticated but deliberately NOT
 * permission-gated (every logged-in user needs dict options to render forms),
 * and the read path is deliberately delete-tolerant: an unknown code returns an
 * empty item list instead of a 404. Both are intentional — but together they mean
 * any single logged-in user can mint an unlimited number of distinct cache keys
 * just by walking {@code /dict/aaa}, {@code /dict/aab}, ... Every one of those
 * requests used to add a permanent entry to an unbounded {@code ConcurrentHashMap},
 * so the map grew without limit for the life of the JVM. Every other cache in the
 * project is a Caffeine cache with a {@code maximumSize} (see
 * {@code app.cache.specs} / {@code CacheConfig}); this one must be bounded too.
 */
@ExtendWith(MockitoExtension.class)
class DictQueryServiceCacheBoundTest {

    @Mock DictItemMapper itemMapper;

    private DictQueryService service;

    @BeforeEach
    void setUp() {
        service = new DictQueryService(itemMapper,
                new DictJsonCodec(tools.jackson.databind.json.JsonMapper.builder().build()));
        lenient().when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    }

    @Test
    void unknownCodesCannotGrowTheCacheWithoutBound() {
        for (int i = 0; i < 20_000; i++) {
            assertThat(service.read("junk_code_" + i).items()).isEmpty();
        }

        assertThat(service.cachedCodes())
                .as("cache keyed by a caller-supplied path variable must be bounded")
                .isLessThanOrEqualTo(DictQueryService.MAX_CACHED_CODES);
    }

    @Test
    void aRealCodeIsStillCachedAndEvictable() {
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.read("some_managed_code");
        assertThat(service.cachedCodes()).isEqualTo(1);

        service.evict("some_managed_code");
        assertThat(service.cachedCodes()).isZero();
    }
}
