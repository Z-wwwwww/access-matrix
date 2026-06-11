package com.platform.system.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dev only：起動時に {@code frontend/src/lang/generated/permissions.&lt;lang&gt;.json} を
 * パッチし、新規 permission code を {@code __TODO__} で埋め、削除された code は残す
 * （既存翻訳は変更しない；削除は CI / 人手判断に任せる）。
 *
 * <p>本クラスはコード常量から「鍵集合」のみ生成する。「翻訳」は人が書く。
 * 翻訳忘れは CI が grep {@code __TODO__} で拾う方針。
 *
 * <p>出力ファイル例：
 * <pre>{@code
 * // frontend/src/lang/generated/permissions.ja_JP.json
 * {
 *   "user:read": "ユーザー閲覧",
 *   "user:delete": "__TODO__"
 * }
 * }</pre>
 */
@Component
public class I18nPermissionPatcher {

    private static final Logger log = LoggerFactory.getLogger(I18nPermissionPatcher.class);
    private static final String TODO = "__TODO__";
    private static final List<String> LANGS = List.of("ja_JP", "en", "zh_CN", "zh_TW", "ko_KR");

    /**
     * frontend ディレクトリへのパス。デフォルトはモノレポ構成（backend と並列に frontend）。
     * 別レポ構成の場合は {@code app.permission.i18n.frontend-dir} で上書き。
     *
     * <p>相対パスは {@code user.dir} から親方向へ遡って解決する。起動方法によって
     * {@code user.dir} が {@code backend/} だったり {@code backend/core-bootstrap/}
     * （IDEA のモジュール実行や {@code spring-boot:run} のフォーク先）だったりするため、
     * 単純な resolve だと {@code backend/frontend/} という偽ディレクトリを作ってしまう。
     * 実在判定は {@code package.json} の有無で行い、見つからなければパッチをスキップする。
     */
    @Value("${app.permission.i18n.frontend-dir:../frontend}")
    private String frontendDir;

    public void patch(Set<String> codes) throws IOException {
        Path frontend = resolveFrontendDir();
        if (frontend == null) {
            log.warn("[I18nPatcher] frontend dir not found (user.dir={}, app.permission.i18n.frontend-dir={}) — skipping i18n patch",
                    System.getProperty("user.dir"), frontendDir);
            return;
        }
        Path generated = frontend.resolve("src/lang/generated");
        if (!Files.exists(generated)) {
            Files.createDirectories(generated);
        }
        for (String lang : LANGS) {
            Path file = generated.resolve("permissions." + lang + ".json");
            Map<String, String> current = readExisting(file);
            int added = 0;
            for (String code : codes) {
                if (!current.containsKey(code)) {
                    current.put(code, TODO);
                    added++;
                }
            }
            if (added > 0 || !Files.exists(file)) {
                writeSorted(file, current);
                log.info("[I18nPatcher] {}: +{} key(s), total={}", file.getFileName(), added, current.size());
            }
        }
    }

    /**
     * 設定が絶対パスならそのまま信用する。相対パスは {@code user.dir} から親へ遡り、
     * 各階層で resolve した候補に {@code package.json} が実在する最初のものを採用。
     * 見つからなければ {@code null}（呼び出し側でスキップ）。
     */
    private Path resolveFrontendDir() {
        Path raw = Paths.get(frontendDir);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        Path base = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path dir = base; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(raw).normalize();
            if (Files.isRegularFile(candidate.resolve("package.json"))) {
                return candidate;
            }
        }
        return null;
    }

    /** 既存 JSON を緩めにパース。書式エラーや欠損は空 Map で復元する（破壊的に上書きしない）。 */
    private Map<String, String> readExisting(Path file) throws IOException {
        if (!Files.exists(file)) return new HashMap<>();
        String text = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (text.isEmpty() || !text.startsWith("{")) return new HashMap<>();
        Map<String, String> out = new HashMap<>();
        // 自前 mini-parser：依存追加を避けるため、"key": "value" のフラット形式だけサポート。
        // 本ファイルは patcher 自身しか書かないので、必ずこの形式に従う前提。
        Pattern p = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(text);
        while (m.find()) {
            out.put(unescape(m.group(1)), unescape(m.group(2)));
        }
        return out;
    }

    /** code 昇順で安定出力。差分が小さいので git diff も読みやすい。 */
    private void writeSorted(Path file, Map<String, String> map) throws IOException {
        Map<String, String> sorted = new TreeMap<>(map);
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            sb.append("  \"").append(escape(e.getKey())).append("\": \"")
              .append(escape(e.getValue())).append("\"");
            if (++i < sorted.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("}\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
