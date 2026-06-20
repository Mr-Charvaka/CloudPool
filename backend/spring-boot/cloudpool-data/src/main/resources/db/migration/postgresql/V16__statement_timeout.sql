-- V16: Enable a default statement timeout to kill long-running queries.
-- Prevents runaway analytical queries or unbounded scans from exhausting
-- database resources.
--
-- The original version of this migration was:
--
--   ALTER ROLE CURRENT_USER SET statement_timeout = '30s';
--
-- This is invalid Postgres syntax. ALTER ROLE's "name" parameter must be a
-- literal role identifier — CURRENT_USER is a function/keyword evaluated at
-- query time, not a literal Postgres will substitute when parsing the
-- ALTER ROLE statement. This migration would fail at apply time with
-- something like `role "current_user" does not exist`, breaking the
-- Flyway migration chain for any fresh deployment against real Postgres.
--
-- We also can't just hardcode the actual role name here: it's supplied at
-- deploy time via SPRING_DATASOURCE_USERNAME and isn't a fixed literal we
-- can safely bake into a migration file across environments. Same problem
-- with hardcoding a database name for ALTER DATABASE.
--
-- The fix: apply the timeout at the database level (affects every role
-- that connects to this database, which is what we actually want — a
-- safety net for the whole application, not just one role) using
-- current_database() to resolve the actual database name dynamically at
-- migration time, via a DO block since ALTER DATABASE also requires a
-- literal name and can't take a function call directly either.
DO $$
BEGIN
    EXECUTE format(
        'ALTER DATABASE %I SET statement_timeout = %L',
        current_database(),
        '30s'
    );
END
$$;
