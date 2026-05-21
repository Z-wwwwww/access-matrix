# 组件注册表（强制维护）

所有新组件必须在生成后自动追加记录。

## 使用规则

1. 生成组件前必须读取此文件
2. **语义指纹查重**：匹配任一条视为重复，禁止新建 —— 调用同一后端 API、props 签名语义相同、命名仅是同义词替换（`List`/`Picker`/`Select`/`Selector`/`Dropdown` 在"下拉选择"语义下全算同类）
3. 必须优先复用已有组件；需要扩展就加 prop，不要新建
4. 生成组件后必须自动追加记录
5. 删除组件时必须同步删除此表中的对应行

## 命名规范（强制）

| 后缀 | 语义 |
|------|------|
| `{Entity}Picker` | 单选下拉选择器 |
| `{Entity}MultiPicker` | 多选下拉选择器 |
| `{Entity}Cascader` | 级联选择器 |
| `{Entity}SearchModal` | 弹窗式搜索+选择 |
| `{Entity}Table` | 表格展示组件 |

禁用作为"下拉选择器"命名：`{Entity}List` / `{Entity}Select` / `{Entity}Selector` / `{Entity}Dropdown`。`List` 后缀保留给列表页（`src/views/`）或纯展示的多条数据渲染组件。

---

| 名称 | 路径 | 用途 | 作者 | 日期 |
|------|------|------|------|------|
| DataTable | src/components/shared/DataTable | 共通データテーブル（分页・pageSize选择・列头筛选・loading/empty・slot自定义列） | AI | 2026-04-09 |
| ColumnFilter | src/components/shared/DataTable | 列ヘッダーフィルター（DataTable 内蔵、单独も利用可） | AI | 2026-04-09 |
| DatePicker | src/components/ui/DatePicker.vue | 単一日付選択（カレンダーUI、Teleportポップアップ、クリア・今日ボタン） | AI | 2026-04-09 |
| DateRangePicker | src/components/ui/DateRangePicker.vue | 日付期間選択（開始日~終了日、終了日<開始日を禁止、範囲ハイライト） | AI | 2026-04-09 |
| DateTimePicker | src/components/ui/DateTimePicker.vue | 日時選択（カレンダー＋時刻入力、確定ボタン、今すぐ） | AI | 2026-04-09 |
| TimePicker | src/components/ui/TimePicker.vue | 時刻選択（HH:mm[:ss]、ドロップダウンリスト、直接入力可） | AI | 2026-04-09 |
| ToastContainer | src/components/shared/ToastContainer.vue | グローバルToast通知（success/error/warning/info、右上スライドイン、自動消滅） | AI | 2026-04-09 |
| Input | src/components/ui/Input.vue | テキスト入力（hover時クリアボタン表示） | AI | 2026-04-09 |
| Select | src/components/ui/Select.vue | ドロップダウン選択（Teleportポップアップ、圆润UI、チェックマーク付き、`multiple` prop で複数選択モード対応・modelValue は Array） | AI | 2026-04-09 |
| Switch | src/components/ui/Switch.vue | トグルスイッチ（checkedValue/uncheckedValue 対応、ステータス切替等に使用） | AI | 2026-04-10 |
| ExportFileButton | src/components/shared/ExportFileButton.vue | 汎用ファイルエクスポートボタン（URL指定POST→blob→ダウンロード、where/filtered/selection対応） | AI | 2026-04-12 |
| UserPicker | src/components/shared/UserPicker.vue | アクティブユーザー下拉選択器（/admin/user/list?page=1&size=500 を取得し {label,value} に変換、検索可能、clearable） | AI | 2026-05-21 |
| DictPicker | src/components/shared/DictPicker.vue | 辞書コード下拉選択器（dictCode で `useDict` composable から取得、HTTP 不要、exclusion 対応） | AI | 2026-05-21 |
| Checkbox | src/components/ui/Checkbox.vue | チェックボックス（v-model boolean / 配列モード `:value` 対応、`label` / slot、`indeterminate` / `error` 対応、全プロジェクト統一スタイル） | AI | 2026-04-13 |
| Radio | src/components/ui/Radio.vue | ラジオボタン単体（v-model + `:value` + `name`、カスタムラベルは default slot） | AI | 2026-04-13 |
| Collapse | src/components/ui/Collapse.vue | 折りたたみパネル（v-model open、`title` / `extra` slot、variant: card/bordered/plain、`defaultOpen`/`disabled` 対応） | AI | 2026-04-13 |
| Tabs | src/components/ui/Tabs.vue | タブコンポーネント（`:items=[{value,label,visible?,disabled?,icon?,badge?}]` + v-model、variant: underline/pill/segmented、sticky 対応、TabsContent は radix-vue から直接） | AI | 2026-04-13 |
| RadioGroup | src/components/ui/RadioGroup.vue | ラジオグループ（`:options=[{label,value}]` で一括描画、`direction=horizontal/vertical`、dicts と直結可能） | AI | 2026-04-13 |
| Drawer | src/components/ui/Drawer.vue | スライドインドロワー（右/左、title/footer slot、Dialog と同じ使用感、closeOnOverlay 対応） | AI | 2026-04-15 |
| DateRangeSelector | src/components/shared/DateRangeSelector.vue | 日付範囲＋曜日フィルター選択（DateRangePicker + 曜日トグル → YYYY-MM-DD[] emit） | AI | 2026-04-15 |
| FileDownloadLink | src/components/shared/FileDownloadLink.vue | テーブル内ファイルダウンロードリンク（FileArchive+Download アイコン+カード風ボタン、`valid=false` で muted 状態、@download emit） | AI | 2026-04-17 |
| LoadingOverlay | src/components/shared/LoadingOverlay.vue | 全画面ローディング遮罩（`visible`/`message` props、backdrop-blur + カード風スピナー、fade Transition、未指定時は `common.message.loading` を表示） | AI | 2026-04-20 |
| SwitchField | src/components/shared/SwitchField.vue | ラベル付きSwitchフィールド（`label`/`description`(title)/`helpText`(?アイコン+title)/`disabledBadge`/`disabled`/`inline` 対応、`stacked`(form 默认) と `inline`(filter bar 横排) 二种 layout、`checkedValue`/`uncheckedValue` 透传 Switch） | AI | 2026-05-09 |
| KpiLineage | src/components/shared/KpiLineage.vue | KPI 算法解説血縁図（3 列 sources→components→KPI、SVG 貝塞曲線で接続、`values` prop で例値埋込、`role:'factor'`+`kpiRef` で他 KPI への因子依存表現+`@drill` emit、`hasAnySources` で 2/3 列自動切替、hover で関連カード+path ハイライト、Ratio/因子分解/Counter 三形態対応） | AI | 2026-05-09 |
