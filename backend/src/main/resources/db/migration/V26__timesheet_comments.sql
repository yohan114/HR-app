-- V26: Add comments column to timesheet header
ALTER TABLE timesheet ADD COLUMN IF NOT EXISTS comments text;
