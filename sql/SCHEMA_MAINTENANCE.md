# Schema Maintenance

## Current Source Of Truth

This project currently manages schema changes with checked-in SQL files instead of a runtime Flyway dependency.

- `sql/lantu_connect.sql` is the baseline snapshot for a fresh database.
- `sql/incremental/V*.sql` is the ordered incremental chain used by the current schema.
- `flyway_schema_history` can exist in deployed databases as historical metadata, but the application does not apply migrations automatically at startup.

## Operating Rules

- Every schema change after the baseline must be added as a new `sql/incremental/V{next}__*.sql` file.
- Destructive changes such as `DROP TABLE` must include a short reason in the SQL header and should be backed up before execution on a shared database.
- Large table consolidations should be staged: create the target table, backfill through a temporary shadow step only while code is being switched, then make all runtime code read/write the target table and remove legacy objects before closure.
- After adding or removing a supported table, update `sql/maintenance/drop-orphan-tables.ps1` so the orphan-table allowlist stays aligned with the current schema.
- Do not add a table only to `sql/lantu_connect.sql`; that makes existing environments drift.

## Current Drift Note

Some existing databases have `flyway_schema_history` entries only up to V43 while the live schema already contains later structures. Treat the incremental SQL files as authoritative unless Flyway runtime migration is formally reintroduced.
