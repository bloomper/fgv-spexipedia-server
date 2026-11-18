-- Spex packages (spec 5.14): the zip arrives in parts and can be resumed, so its size and how much has arrived are kept
ALTER TABLE spex_package
    ADD COLUMN zip_size     BIGINT NULL AFTER zip_file_name,
    ADD COLUMN zip_received BIGINT NOT NULL DEFAULT 0 AFTER zip_size;

-- The spex books (Chalmersspex I, II …) are kept as recordings of their own medium, their numbers as the tracks.
-- Övrigt stays last.
UPDATE kind SET position = 6 WHERE uuid = '01900000-0000-7000-8000-000000000305';

INSERT INTO kind (uuid, type, use_for, value_type, name_sv, name_en, aliases, position, created_by, created_at)
VALUES ('01900000-0000-7000-8000-000000000306', 'MEDIUM', '', NULL, 'Spexbok', 'Spex book', 'spexbok\nspexböcker\nsångbok\nsånghäfte', 5, 'system', NOW(6));
