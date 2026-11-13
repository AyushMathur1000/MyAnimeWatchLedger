-- Deliberately invalid migration used only by DatabaseMigrationRunnerTest.
CREATE TABLE should_rollback (
    id INTEGER PRIMARY KEY
);
THIS IS NOT VALID SQL;
