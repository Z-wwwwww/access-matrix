-- V49: record the error code on failed audit rows so monitoring can tell a
-- deliberate business rejection apart from an unexpected server error.
--
-- core_oplog.success is a plain boolean: a 700 "value is in use, disable it
-- instead" business rejection and an unhandled 500 both land as success=false,
-- so the platform dashboard's "API errors (24h)" metric counted normal,
-- expected rejections as errors. OpLogAspect now also stores the error code:
-- a BusinessException carries its ErrorCode (400/401/403/404/700..730), an
-- unexpected exception is tagged 500. The dashboard then counts only
-- error_code = 500 as a real error.
--
-- Nullable: null on success rows and on pre-V49 rows (which therefore drop out
-- of the "real errors" count — correct for the historical business rejections,
-- and the 24h window self-heals within a day).

ALTER TABLE core_oplog ADD COLUMN IF NOT EXISTS error_code INT;

COMMENT ON COLUMN core_oplog.error_code IS
    'Error code when success=false: BusinessException.ErrorCode (4xx/7xx = expected '
    'rejection) or 500 (unexpected server error). NULL on success / pre-V49 rows.';
