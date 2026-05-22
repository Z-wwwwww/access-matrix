package com.platform.system.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.security.PermissionRegistry;
import com.platform.core.common.security.RequiresPermission;
import com.platform.system.rbac.entity.PermissionEntity;
import com.platform.system.rbac.entity.RolePermissionEntity;
import com.platform.system.rbac.mapper.PermissionMapper;
import com.platform.system.rbac.mapper.RolePermissionMapper;
import com.platform.system.rbac.service.PermissionCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 起動時に「コード常量 ↔ {@code @RequiresPermission} 注解 ↔ DB 字典」三者の一致性を強制する。
 *
 * <p>段階：
 * <ol>
 *   <li><b>注解スキャン</b>：{@code @Controller} bean を {@link ApplicationContext} から拾い、
 *       各メソッドの {@code @RequiresPermission} を集める（{@code @RestController} は {@code @Controller} を含む）</li>
 *   <li><b>字面量チェック</b>：集めた code のうち、{@link PermissionRegistry} にも通配にも該当しない →
 *       字面量直書きの可能性 → <b>常に fail-fast</b>（開発者の明らかなバグなので例外なし）</li>
 *   <li><b>DB upsert</b>：{@code PermissionRegistry} にあって DB にない code を {@code is_built_in=1} で挿入</li>
 *   <li><b>孤児追従</b>：DB built-in にあって {@code PermissionRegistry} にない code は
 *       <b>コードに合わせて自動 soft delete</b>。同時にひも付く {@code role_permission} も soft delete。
 *       これにより「コードがソース・オブ・トゥルース、DB はその影」関係を完全に維持できる
 *       （以前は strict モードで fail-fast していたが、運用負担が大きいため自動追従に変更）</li>
 *   <li><b>(dev only) i18n パッチ</b>：{@link I18nPermissionPatcher} を呼んで 5 言語ファイルの
 *       {@code permission.<code>} key を {@code __TODO__} で補う</li>
 * </ol>
 *
 * <p>注意：孤児を自動消すので、たとえばブランチを巻き戻すと前バージョンで参照されていた権限が
 * 一時的に消える可能性がある。実装としては soft delete なので {@code mark=1} に戻せば復活する。
 * 万一の事故時に手動復旧できる余地は残してある。
 */
@Component
public class PermissionConsistencyGuard {

    private static final Logger log = LoggerFactory.getLogger(PermissionConsistencyGuard.class);

    private final ApplicationContext appContext;
    private final PermissionMapper mapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionCacheService cacheService;
    private final I18nPermissionPatcher patcher;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    public PermissionConsistencyGuard(ApplicationContext appContext,
                                      PermissionMapper mapper,
                                      RolePermissionMapper rolePermissionMapper,
                                      PermissionCacheService cacheService,
                                      I18nPermissionPatcher patcher) {
        this.appContext = appContext;
        this.mapper = mapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.cacheService = cacheService;
        this.patcher = patcher;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void verify() {
        Set<String> registered = PermissionRegistry.allCodes();
        Set<String> annotated  = scanAnnotations();
        Set<String> inDb       = loadBuiltInCodesFromDb();

        // 1) 字面量チェック：注解 - 常量 - 通配 の差集合は字面量取り残しか未登録ミス
        //    これは明らかな開発バグなので例外なく fail-fast。
        Set<String> literals = annotated.stream()
                .filter(c -> !registered.contains(c) && !PermissionRegistry.isWildcard(c))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!literals.isEmpty()) {
            die("検出した未登録の @RequiresPermission（字面量直書きの可能性）: " + literals
                    + " — PermissionCode.registerAll() 経由の常量参照に置き換えてください");
        }

        // 2) DB に欠けている常量を upsert（is_built_in=1）
        Set<String> missing = new TreeSet<>(registered);
        missing.removeAll(inDb);
        for (String code : missing) {
            upsert(code);
            log.info("[PermissionGuard] auto-seeded built-in permission: {}", code);
        }

        // 3) 孤児追従：コードから消えた権限は DB も soft delete し、role-permission も連動して切る。
        //    重要：通配 (*:*, xxx:*) は意図的に常量に含めていないので孤児扱いしてはいけない。
        //    PermissionMatcher の wildcard ルールで動的に解釈される、削除すると SUPER_ADMIN が
        //    全権限を失う事故になる。
        Set<String> orphans = new TreeSet<>(inDb);
        orphans.removeAll(registered);
        orphans.removeIf(PermissionRegistry::isWildcard);
        int relationsRevoked = 0;
        for (String code : orphans) {
            relationsRevoked += softDeleteOrphan(code);
            log.warn("[PermissionGuard] removed orphan permission '{}' (no longer declared in code)", code);
        }

        if (!missing.isEmpty() || !orphans.isEmpty()) {
            cacheService.evictAll();
        }

        log.info("[PermissionGuard] OK — registered={}, annotated={}, db_before={}, seeded={}, orphans_removed={} (with {} role-bindings revoked)",
                registered.size(), annotated.size(), inDb.size(), missing.size(), orphans.size(), relationsRevoked);

        // 4) dev のみ i18n パッチ
        if (isDevProfile()) {
            try {
                patcher.patch(registered);
            } catch (Exception e) {
                // i18n パッチ失敗は本体の起動を止めない（前後端が同一 repo に居ないケースもある）
                log.warn("[PermissionGuard] i18n patcher failed: {}", e.getMessage());
            }
        }
    }

    private Set<String> scanAnnotations() {
        Set<String> out = new LinkedHashSet<>();
        Map<String, Object> controllers = appContext.getBeansWithAnnotation(Controller.class);
        for (Object bean : controllers.values()) {
            Class<?> cls = AopUtils.getTargetClass(bean);
            for (Method m : cls.getDeclaredMethods()) {
                RequiresPermission ann = AnnotationUtils.findAnnotation(m, RequiresPermission.class);
                if (ann == null) continue;
                String[] anyOf = ann.anyOf();
                if (anyOf != null && anyOf.length > 0) {
                    Stream.of(anyOf).filter(s -> s != null && !s.isBlank()).forEach(out::add);
                } else if (ann.value() != null && !ann.value().isBlank()) {
                    out.add(ann.value());
                }
            }
        }
        return out;
    }

    private Set<String> loadBuiltInCodesFromDb() {
        return mapper.selectList(new QueryWrapper<PermissionEntity>()
                        .select("code").eq("mark", 1).eq("is_built_in", 1))
                .stream().map(PermissionEntity::getCode)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void upsert(String code) {
        // 念のため soft-deleted (mark=0) 行が同 code で残っていれば復活させる。
        // 過去に手動で SET mark=0 した残骸、または孤児追従で消したものを再度宣言した場合への対応。
        List<PermissionEntity> existing = mapper.selectList(
                new QueryWrapper<PermissionEntity>().eq("code", code));
        PermissionRegistry.Entry entry = PermissionRegistry.get(code);
        if (!existing.isEmpty()) {
            PermissionEntity e = existing.get(0);
            e.setMark(1);
            e.setIsBuiltIn(1);
            e.setResource(entry.resource());
            e.setAction(entry.action());
            e.setModule(entry.module());
            if (e.getName() == null || e.getName().isBlank()) e.setName(code);
            mapper.updateById(e);
            return;
        }
        PermissionEntity e = new PermissionEntity();
        e.setId(IdGenerator.ulid());
        e.setCode(code);
        e.setName(code);   // 表示用 name は i18n に任せる。DB の name は fallback。
        e.setResource(entry.resource());
        e.setAction(entry.action());
        e.setModule(entry.module());
        e.setIsBuiltIn(1);
        e.setMark(1);
        mapper.insert(e);
    }

    /**
     * 孤児 permission 行（および紐づく role-permission 関係）を soft delete。
     * 戻り値は失効した role-permission 行数。
     */
    private int softDeleteOrphan(String code) {
        List<PermissionEntity> rows = mapper.selectList(
                new QueryWrapper<PermissionEntity>().eq("code", code).eq("mark", 1));
        if (rows.isEmpty()) return 0;
        int relations = 0;
        LocalDateTime now = LocalDateTime.now();
        for (PermissionEntity e : rows) {
            e.setMark(0);
            mapper.updateById(e);
            // 同じ permission_id を指している role-permission を全件 soft delete。
            // BaseMapper#update(Wrapper) で UPDATE SET ... WHERE ... 一括発行。
            Integer affected = rolePermissionMapper.update(null,
                    new UpdateWrapper<RolePermissionEntity>()
                            .eq("permission_id", e.getId())
                            .eq("mark", 1)
                            .set("mark", 0)
                            .set("update_time", now));
            relations += (affected == null ? 0 : affected);
        }
        return relations;
    }

    private boolean isDevProfile() {
        if (activeProfile == null || activeProfile.isBlank()) return false;
        return Arrays.stream(activeProfile.split(","))
                .map(String::trim)
                .anyMatch(p -> p.equalsIgnoreCase("dev") || p.equalsIgnoreCase("local"));
    }

    private void die(String msg) {
        throw new IllegalStateException("[PermissionGuard][FATAL] " + msg);
    }
}
