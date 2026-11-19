-- Book kinds (spec 4.6): what sort of book it is. Data kept by admins, like recording media; a new book gets the first.
INSERT INTO kind (uuid, type, use_for, value_type, name_sv, name_en, aliases, position, created_by, created_at)
VALUES ('01900000-0000-7000-8000-000000000601', 'BOOK', '', NULL, 'Spexbok', 'Spex book', 'spexbok\nspexböcker\nchalmersspex', 1, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000602', 'BOOK', '', NULL, 'Sångbok', 'Songbook', 'sångbok\nsånghäfte\nsångböcker', 2, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000603', 'BOOK', '', NULL, 'Jubileumsbok', 'Anniversary book', 'jubileumsbok\njubileumsskrift\nminnesskrift', 3, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000604', 'BOOK', '', NULL, 'Övrigt', 'Other', 'övrigt\nannat\nbok', 4, 'system', NOW(6));

-- The books so far are the spex books
ALTER TABLE book ADD COLUMN kind_id BIGINT NULL AFTER title;
UPDATE book SET kind_id = (SELECT id FROM kind WHERE uuid = '01900000-0000-7000-8000-000000000601');
ALTER TABLE book
    MODIFY kind_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_book_kind FOREIGN KEY (kind_id) REFERENCES kind (id);
