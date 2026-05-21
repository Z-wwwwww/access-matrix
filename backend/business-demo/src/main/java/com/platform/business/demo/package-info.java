/**
 * Demo business module.
 *
 * <p>Exists to show the data-scope feature in action via a single non-trivial
 * entity ({@code demo_task}). Five seeded roles map 1:1 to the five
 * {@code data_scope} modes (ALL / DEPT_AND_SUB / DEPT / SELF / CUSTOM) and
 * five seeded users hold one role each. Logging in as each user lists a
 * different subset of the same task data, exposing exactly what each scope
 * mode implies for visibility.
 *
 * <p>This module is <b>not</b> meant for production use. Treat it as a
 * worked example: copy the {@code @DataScope} + {@link
 * com.platform.core.infrastructure.security.rbac.DataScopeHelper#apply}
 * pattern when building a real business module.
 */
package com.platform.business.demo;
