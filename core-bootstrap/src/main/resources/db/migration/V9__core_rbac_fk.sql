-- V9: Referential integrity for RBAC link tables, plus a clarifying comment
-- on core_rbac_dept.leader_user_id (memo-only, no permission semantics).
--
-- Stage 4 wired all bindings through the admin API which now also does an
-- existence pre-check in service code; this migration adds the database
-- constraints as a second line of defence so that ANY future code path
-- (Flyway seeds, ad-hoc SQL, accidental imports) cannot leave dangling
-- references behind.
--
-- ON DELETE RESTRICT: hard deletes of parent rows that are still referenced
-- will fail loudly. The standard delete path is soft-delete (mark=0), which
-- leaves the id intact and is therefore unaffected.

-- ============================================
-- Phase 1: scrub any pre-existing orphan link rows so the constraint creation
-- below cannot fail mid-deployment. We check the existence of the parent row
-- (ignoring mark) — only a fully missing PK is an orphan.
-- ============================================

DELETE FROM core_rbac_user_role ur
 WHERE NOT EXISTS (SELECT 1 FROM core_auth_user      u WHERE u.id = ur.user_id)
    OR NOT EXISTS (SELECT 1 FROM core_rbac_role      r WHERE r.id = ur.role_id);

DELETE FROM core_rbac_role_permission rp
 WHERE NOT EXISTS (SELECT 1 FROM core_rbac_role       r WHERE r.id = rp.role_id)
    OR NOT EXISTS (SELECT 1 FROM core_rbac_permission p WHERE p.id = rp.permission_id);

DELETE FROM core_rbac_role_menu rm
 WHERE NOT EXISTS (SELECT 1 FROM core_rbac_role r WHERE r.id = rm.role_id)
    OR NOT EXISTS (SELECT 1 FROM core_rbac_menu m WHERE m.id = rm.menu_id);

DELETE FROM core_rbac_role_dept rd
 WHERE NOT EXISTS (SELECT 1 FROM core_rbac_role r WHERE r.id = rd.role_id)
    OR NOT EXISTS (SELECT 1 FROM core_rbac_dept d WHERE d.id = rd.dept_id);

-- ============================================
-- Phase 2: add the FK constraints. Idempotent guards via DO blocks so a re-run
-- (e.g. repair-on-migrate) does not error out on "constraint already exists".
-- ============================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_core_rbac_user_role_user') THEN
        ALTER TABLE core_rbac_user_role
          ADD CONSTRAINT fk_core_rbac_user_role_user
              FOREIGN KEY (user_id) REFERENCES core_auth_user(id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_core_rbac_user_role_role') THEN
        ALTER TABLE core_rbac_user_role
          ADD CONSTRAINT fk_core_rbac_user_role_role
              FOREIGN KEY (role_id) REFERENCES core_rbac_role(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_core_rbac_role_perm_role') THEN
        ALTER TABLE core_rbac_role_permission
          ADD CONSTRAINT fk_core_rbac_role_perm_role
              FOREIGN KEY (role_id) REFERENCES core_rbac_role(id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_core_rbac_role_perm_perm') THEN
        ALTER TABLE core_rbac_role_permission
          ADD CONSTRAINT fk_core_rbac_role_perm_perm
              FOREIGN KEY (permission_id) REFERENCES core_rbac_permission(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_core_rbac_role_menu_role') THEN
        ALTER TABLE core_rbac_role_menu
          ADD CONSTRAINT fk_core_rbac_role_menu_role
              FOREIGN KEY (role_id) REFERENCES core_rbac_role(id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_core_rbac_role_menu_menu') THEN
        ALTER TABLE core_rbac_role_menu
          ADD CONSTRAINT fk_core_rbac_role_menu_menu
              FOREIGN KEY (menu_id) REFERENCES core_rbac_menu(id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_core_rbac_role_dept_role') THEN
        ALTER TABLE core_rbac_role_dept
          ADD CONSTRAINT fk_core_rbac_role_dept_role
              FOREIGN KEY (role_id) REFERENCES core_rbac_role(id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_core_rbac_role_dept_dept') THEN
        ALTER TABLE core_rbac_role_dept
          ADD CONSTRAINT fk_core_rbac_role_dept_dept
              FOREIGN KEY (dept_id) REFERENCES core_rbac_dept(id) ON DELETE RESTRICT;
    END IF;
END $$;

-- ============================================
-- Phase 3: clarify the (permission-irrelevant) leader_user_id column.
-- ============================================
COMMENT ON COLUMN core_rbac_dept.leader_user_id IS
    'Informational memo (department head). Not consulted by permission or '
    'data-scope logic; the admin UI populates it via the user-list dropdown.';
