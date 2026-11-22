-- Sections of spex pages as data (spec 4.2, 4.2.1 and 4.6): section kinds with a content, a subtitle and the default
-- order; image kinds belong to an image section; a spex may keep its own order of sections, with sections that show
-- material from the other applications.

-- Section kinds ------------------------------------------------------------------------------------------------------

ALTER TABLE kind
    -- Section kinds only: GROUP, CHARACTERS, IMAGES, MANUSCRIPT, MUSIC, RECORDS, REBUS, VEST, BOARD, TASKS, LINKS,
    -- SPEXFLIX_VIDEO, SPEXGRAM, SPEXIFY_ALBUM, ARCHIVE_DOCUMENT
    ADD COLUMN content     VARCHAR(20)  NULL AFTER value_type,
    ADD COLUMN subtitle_sv VARCHAR(200) NULL AFTER name_en,
    ADD COLUMN subtitle_en VARCHAR(200) NULL AFTER subtitle_sv,
    -- Image kinds of use SECTION: the image section (a section kind of content IMAGES) they belong to
    ADD COLUMN section_id  BIGINT       NULL AFTER use_for,
    ADD CONSTRAINT fk_kind_section FOREIGN KEY (section_id) REFERENCES kind (id),
    DROP INDEX idx_kind_type,
    ADD INDEX idx_kind_type (type, use_for, section_id, position);

-- The defaults, in the pages' default order, with fixed ids. Every content but IMAGES and the four from the other
-- applications has exactly one section kind; those four are not on pages until an author adds one.
INSERT INTO kind (uuid, type, use_for, content, name_sv, name_en, subtitle_sv, subtitle_en, aliases, position, created_by, created_at)
VALUES ('01900000-0000-7000-8000-000000000701', 'SECTION', '', 'GROUP', 'Ensemblen', 'The ensemble', 'Gruppbilden', 'The group photo', 'gruppbild\nensemble', 1, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000702', 'SECTION', '', 'CHARACTERS', 'Roller', 'Characters', 'Rollfigurer och de som spelade dem', 'Characters and who played them', 'rollfigurer\nkaraktärer', 2, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000703', 'SECTION', '', 'IMAGES', 'Scenografi', 'Scenography', 'Fond, scen och rekvisita', 'Backdrop, stage and props', 'scenografi\nscenography', 3, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000704', 'SECTION', '', 'MANUSCRIPT', 'Manus och program', 'Script and programme', 'Handling och scener', 'Synopsis and scenes', 'manus\nhandling', 4, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000705', 'SECTION', '', 'MUSIC', 'Musik', 'Music', 'Nummer, melodier och upphovspersoner', 'Numbers, melodies and credits', 'nummer\nsånger', 5, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000706', 'SECTION', '', 'RECORDS', 'Skivor', 'Records', 'Skivorna i Spexify', 'The records in Spexify', 'inspelningar\nskivor', 6, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000707', 'SECTION', '', 'REBUS', 'Programrebus', 'Programme rebus', 'Kan du lösa den?', 'Can you solve it?', 'rebus', 7, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000708', 'SECTION', '', 'VEST', 'Spexvästen', 'The vest', 'Västen och vem som bar den', 'The vest and who wore it', 'väst\nvästen\nvest', 8, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000709', 'SECTION', '', 'BOARD', 'Direktionen', 'The board', 'Spexets direktion', 'The spex''s board', 'direktion\nstyrelse', 9, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000710', 'SECTION', '', 'TASKS', 'Medverkande', 'Crew and cast', 'Vem gjorde vad', 'Who did what', 'funktioner\nmedverkande', 10, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000711', 'SECTION', '', 'IMAGES', 'Övriga bilder', 'Other images', 'Fler bilder från spexet', 'More images from the spex', 'bilder\növrigt\nannat', 11, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000712', 'SECTION', '', 'LINKS', 'Mer om spexet', 'More about the spex', 'I FGV:s andra tjänster', 'In FGV''s other services', 'länkar\nlinks', 12, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000721', 'SECTION', '', 'SPEXFLIX_VIDEO', 'Film', 'Video', 'Från Spexflix', 'From Spexflix', 'video\nfilm\nspexflix', 13, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000722', 'SECTION', '', 'SPEXGRAM', 'Foton', 'Photos', 'Från Spexgram', 'From Spexgram', 'foton\nspexgram', 14, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000723', 'SECTION', '', 'SPEXIFY_ALBUM', 'Skiva', 'Record', 'Från Spexify', 'From Spexify', 'skiva\nalbum\nspexify', 15, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000724', 'SECTION', '', 'ARCHIVE_DOCUMENT', 'Dokument', 'Document', 'Från Spexarkivet', 'From Spexarkivet', 'dokument\nspexarkivet', 16, 'system', NOW(6));

-- Image kinds of the scenography and of the other images belong to those image sections
UPDATE kind
SET use_for    = 'SECTION',
    section_id = (SELECT id FROM (SELECT id FROM kind WHERE uuid = '01900000-0000-7000-8000-000000000703') s)
WHERE type = 'IMAGE' AND use_for = 'SCENOGRAPHY';

UPDATE kind
SET use_for    = 'SECTION',
    section_id = (SELECT id FROM (SELECT id FROM kind WHERE uuid = '01900000-0000-7000-8000-000000000711') s)
WHERE type = 'IMAGE' AND use_for = 'PAGE';

-- Page images: the section is a section kind (an image section, or the vest) --------------------------------------

CREATE TABLE spex_page_image_new
(
    spex_id    BIGINT        NOT NULL,
    section_id BIGINT        NOT NULL,
    -- 0 for the vest, which has no image kinds
    kind_id    BIGINT        NOT NULL DEFAULT 0,
    position   INT           NOT NULL,
    image_id   BIGINT        NOT NULL,
    caption    VARCHAR(1000) NULL,
    PRIMARY KEY (spex_id, section_id, kind_id, position),
    CONSTRAINT fk_spex_page_image_spex_3 FOREIGN KEY (spex_id) REFERENCES spex (id) ON DELETE CASCADE,
    CONSTRAINT fk_spex_page_image_section FOREIGN KEY (section_id) REFERENCES kind (id),
    CONSTRAINT fk_spex_page_image_image_3 FOREIGN KEY (image_id) REFERENCES image (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO spex_page_image_new (spex_id, section_id, kind_id, position, image_id, caption)
SELECT i.spex_id, s.id, i.kind_id, i.position, i.image_id, i.caption
FROM spex_page_image i
         JOIN kind s ON s.uuid = CASE i.section
                                     WHEN 'SCENOGRAPHY' THEN '01900000-0000-7000-8000-000000000703'
                                     WHEN 'VEST' THEN '01900000-0000-7000-8000-000000000708'
                                     ELSE '01900000-0000-7000-8000-000000000711' END;

DROP TABLE spex_page_image;
RENAME TABLE spex_page_image_new TO spex_page_image;

-- What an image shows, now that sections are data: images of image sections are SECTION, of characters CHARACTER
UPDATE image SET kind = 'SECTION' WHERE kind IN ('SCENOGRAPHY', 'VEST');
UPDATE image SET kind = 'CHARACTER' WHERE kind IN ('SKETCH', 'MAKEUP', 'COSTUME');

-- A spex's own order of sections ------------------------------------------------------------------------------------

-- The sections of a spex page in the spex' own order (spec 4.2). A spex without rows shows the default order (a revival
-- its original's). Sections of the contents with one section kind, and image sections, appear at most once; sections
-- from the other applications as often as authors add them.
CREATE TABLE spex_section
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    uuid          CHAR(36)     NOT NULL,
    spex_id       BIGINT       NOT NULL,
    kind_id       BIGINT       NOT NULL,
    position      INT          NOT NULL,
    hidden        BIT          NOT NULL DEFAULT 0,
    -- The section's own heading; the kind's name when null
    title         VARCHAR(200) NULL,
    -- Sections from the other applications: the video, album or document in that application
    ref_id        CHAR(36)     NULL,
    -- Spexgram: the picked items of the album, one id per line; the whole album when empty
    item_ids      TEXT         NULL,
    -- Spexflix: where the video starts, in seconds
    start_seconds INT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_spex_section_uuid UNIQUE (uuid),
    CONSTRAINT fk_spex_section_spex FOREIGN KEY (spex_id) REFERENCES spex (id) ON DELETE CASCADE,
    CONSTRAINT fk_spex_section_kind FOREIGN KEY (kind_id) REFERENCES kind (id),
    INDEX idx_spex_section_spex (spex_id, position)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
