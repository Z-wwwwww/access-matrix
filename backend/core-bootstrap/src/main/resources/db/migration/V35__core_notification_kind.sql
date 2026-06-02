-- V35: notification "kind" — distinguish action-required notifications from
-- plain info ones, so the UI can badge them ("待处理") and they read
-- differently from a normal message.
--
--   kind = 0  info    — FYI; click to view (default, existing rows)
--   kind = 1  action  — needs the recipient to go handle something (confirm /
--                       reject / process). No inline buttons: clicking opens
--                       the record (drawer) via biz_type/biz_id; when the
--                       business decision completes, a NotificationResolvedEvent
--                       marks the row read.

ALTER TABLE core_notification
    ADD COLUMN IF NOT EXISTS kind SMALLINT NOT NULL DEFAULT 0;
