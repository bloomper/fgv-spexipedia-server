-- Kinds as data (spec 4.6), facts on spex pages, more about numbers, unresolved credits, and the import API (5.11)

-- Kinds ---------------------------------------------------------------------------------------------------------------

-- Every kind admins keep: number kinds, credit kinds, recording media, image kinds (per use) and fact kinds. The type
-- and the use are fixed by the code; the kinds within them are data. A kind in use is retired or merged, never deleted.
CREATE TABLE kind
(
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)      NOT NULL,
    -- NUMBER, CREDIT, MEDIUM, IMAGE, FACT
    type             VARCHAR(20)   NOT NULL,
    -- Image kinds only: CHARACTER, SCENOGRAPHY, PAGE; '' for the other types
    use_for          VARCHAR(20)   NOT NULL DEFAULT '',
    -- Fact kinds only: TEXT or DATE
    value_type       VARCHAR(10)   NULL,
    name_sv          VARCHAR(100)  NOT NULL,
    name_en          VARCHAR(100)  NOT NULL,
    -- Other spellings the import recognises, one per line
    aliases          VARCHAR(2000) NOT NULL DEFAULT '',
    position         INT           NOT NULL,
    retired          BIT           NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    last_modified_by VARCHAR(64)   NULL,
    last_modified_at DATETIME(6)   NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_kind_uuid UNIQUE (uuid),
    INDEX idx_kind_type (type, use_for, position)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- The defaults, with fixed ids so that sample data and tests can refer to them. Admins change them as they like.
INSERT INTO kind (uuid, type, use_for, value_type, name_sv, name_en, aliases, position, created_by, created_at)
VALUES ('01900000-0000-7000-8000-000000000101', 'NUMBER', '', NULL, 'Sång', 'Song', 'sång\nlåt\nvisa\nsång, solo med komp\nsång, duett med komp\nsång, trio med komp\nsång, flera med komp\nsång, alla med komp', 1, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000102', 'NUMBER', '', NULL, 'A cappella', 'A cappella', 'acapella\nacapella, kvartett\na capella', 2, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000103', 'NUMBER', '', NULL, 'Instrumental', 'Instrumental', 'instrumentalt\nmellanaktsmusik', 3, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000104', 'NUMBER', '', NULL, 'Ouvertyr', 'Overture', 'ouvertyr\nouverture\noverture', 4, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000105', 'NUMBER', '', NULL, 'Entré', 'Entrance', 'entré\nentre\nentré-akt', 5, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000106', 'NUMBER', '', NULL, 'Scenbyte', 'Scene change', 'scenbyte\nscenväxling', 6, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000107', 'NUMBER', '', NULL, 'Final', 'Finale', 'final\nfinal\naktfinal\nslutfinal', 7, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000108', 'NUMBER', '', NULL, 'Ljudeffekt', 'Sound effect', 'ljudeffekt\nljudeffekter', 8, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000109', 'NUMBER', '', NULL, 'Övrigt', 'Other', 'övrigt\nospecificerad', 9, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000201', 'CREDIT', '', NULL, 'Text', 'Lyrics', 'textförfattare\ntext\nlyrics', 1, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000202', 'CREDIT', '', NULL, 'Musik', 'Music', 'kompositör\nmusik\ncomposer', 2, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000203', 'CREDIT', '', NULL, 'Arrangemang', 'Arrangement', 'arrangör\narrangemang\narranger', 3, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000204', 'CREDIT', '', NULL, 'Koreografi', 'Choreography', 'koreograf\nkoreografi\nchoreographer', 4, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000301', 'MEDIUM', '', NULL, 'LP', 'LP', 'lp\nvinyl', 1, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000302', 'MEDIUM', '', NULL, 'EP', 'EP', 'ep\nsingel', 2, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000303', 'MEDIUM', '', NULL, 'CD', 'CD', 'cd\ncd-skiva', 3, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000304', 'MEDIUM', '', NULL, 'Digital', 'Digital', 'digital\nstreaming', 4, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000305', 'MEDIUM', '', NULL, 'Annat', 'Other', 'annat\nkassett\nvcd', 5, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000401', 'IMAGE', 'CHARACTER', NULL, 'Skiss', 'Sketch', 'skiss\nskisser\nkaraktärsskiss', 1, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000402', 'IMAGE', 'CHARACTER', NULL, 'Sminkskiss', 'Make-up sketch', 'sminkskiss\nsminkskisser\nsmink', 2, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000403', 'IMAGE', 'CHARACTER', NULL, 'Kostymdocka', 'Costume doll', 'kostym\nkostymdocka\ndocka', 3, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000404', 'IMAGE', 'CHARACTER', NULL, 'Övrigt', 'Other', 'övrigt\nannat', 4, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000411', 'IMAGE', 'SCENOGRAPHY', NULL, 'Fond', 'Backdrop', 'fond\nfonder\nfondmålning', 1, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000412', 'IMAGE', 'SCENOGRAPHY', NULL, 'Scen', 'Stage', 'scen\nscenbygge\nscenografi', 2, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000413', 'IMAGE', 'SCENOGRAPHY', NULL, 'Rekvisita', 'Props', 'rekvisita\nprops', 3, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000421', 'IMAGE', 'PAGE', NULL, 'Övriga bilder', 'Other images', 'bilder\növrigt\nannat', 1, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000501', 'FACT', '', 'TEXT', 'Spelplats', 'Venue', 'spelplats\nlokal\nteater\nvenue', 1, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000502', 'FACT', '', 'TEXT', 'Antal föreställningar', 'Number of performances', 'antal föreställningar\nföreställningar', 2, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000503', 'FACT', '', 'TEXT', 'Handlingen utspelar sig', 'Setting', 'tid och plats\nmiljö\nsetting', 3, 'system', NOW(6)),
       ('01900000-0000-7000-8000-000000000504', 'FACT', '', 'TEXT', 'Tagline', 'Tagline', 'tagline\nslogan', 4, 'system', NOW(6));

-- Numbers: kind, where the original song is from, the first line; credits by credit kind ---------------------------

ALTER TABLE spex_number
    ADD COLUMN kind_id    BIGINT       NULL AFTER title,
    ADD COLUMN from_work  VARCHAR(255) NULL AFTER original_artist,
    ADD COLUMN first_line VARCHAR(500) NULL AFTER from_work,
    ADD CONSTRAINT fk_spex_number_kind FOREIGN KEY (kind_id) REFERENCES kind (id);


-- A credit names a spexare (spexare_id), someone outside FGV (name), or, when imported, a name nobody has resolved yet
-- (name with unresolved = 1): shown only to editors until an editor resolves it (spec 5.3)
CREATE TABLE number_credit_new
(
    number_id  BIGINT       NOT NULL,
    kind_id    BIGINT       NOT NULL,
    position   INT          NOT NULL,
    spexare_id CHAR(36)     NULL,
    name       VARCHAR(255) NULL,
    unresolved BIT          NOT NULL DEFAULT 0,
    PRIMARY KEY (number_id, kind_id, position),
    CONSTRAINT fk_number_credit_number_2 FOREIGN KEY (number_id) REFERENCES spex_number (id) ON DELETE CASCADE,
    CONSTRAINT fk_number_credit_kind FOREIGN KEY (kind_id) REFERENCES kind (id),
    INDEX idx_number_credit_unresolved (unresolved)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO number_credit_new (number_id, kind_id, position, spexare_id, name)
SELECT c.number_id, k.id, c.position, c.spexare_id, c.name
FROM number_credit c
         JOIN kind k ON k.uuid = CASE c.kind
                                     WHEN 'LYRICS' THEN '01900000-0000-7000-8000-000000000201'
                                     WHEN 'COMPOSER' THEN '01900000-0000-7000-8000-000000000202'
                                     WHEN 'ARRANGER' THEN '01900000-0000-7000-8000-000000000203'
                                     ELSE '01900000-0000-7000-8000-000000000204' END;

DROP TABLE number_credit;
RENAME TABLE number_credit_new TO number_credit;

-- Recordings: the medium is a kind -----------------------------------------------------------------------------------

ALTER TABLE recording
    ADD COLUMN medium_id BIGINT NULL AFTER medium;

UPDATE recording r
    JOIN kind k ON k.uuid = CASE r.medium
                                WHEN 'LP' THEN '01900000-0000-7000-8000-000000000301'
                                WHEN 'EP' THEN '01900000-0000-7000-8000-000000000302'
                                WHEN 'CD' THEN '01900000-0000-7000-8000-000000000303'
                                WHEN 'DIGITAL' THEN '01900000-0000-7000-8000-000000000304'
                                ELSE '01900000-0000-7000-8000-000000000305' END
SET r.medium_id = k.id;

ALTER TABLE recording
    DROP COLUMN medium,
    MODIFY medium_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_recording_medium FOREIGN KEY (medium_id) REFERENCES kind (id);


-- Character images: the kind is an image kind for characters --------------------------------------------------------

ALTER TABLE character_image
    ADD COLUMN kind_id BIGINT NULL AFTER kind;

UPDATE character_image ci
    JOIN kind k ON k.uuid = CASE ci.kind
                                WHEN 'SKETCH' THEN '01900000-0000-7000-8000-000000000401'
                                WHEN 'MAKEUP' THEN '01900000-0000-7000-8000-000000000402'
                                WHEN 'COSTUME' THEN '01900000-0000-7000-8000-000000000403'
                                ELSE '01900000-0000-7000-8000-000000000404' END
SET ci.kind_id = k.id;

ALTER TABLE character_image
    DROP COLUMN kind,
    MODIFY kind_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_character_image_kind FOREIGN KEY (kind_id) REFERENCES kind (id);

-- Page images: a section (SCENOGRAPHY, VEST, OTHER) and, for scenography and other images, an image kind -------------

CREATE TABLE spex_page_image_new
(
    spex_id  BIGINT        NOT NULL,
    section  VARCHAR(20)   NOT NULL,
    -- 0 for the vest, which has no kinds
    kind_id  BIGINT        NOT NULL DEFAULT 0,
    position INT           NOT NULL,
    image_id BIGINT        NOT NULL,
    caption  VARCHAR(1000) NULL,
    PRIMARY KEY (spex_id, section, kind_id, position),
    CONSTRAINT fk_spex_page_image_spex_2 FOREIGN KEY (spex_id) REFERENCES spex (id) ON DELETE CASCADE,
    CONSTRAINT fk_spex_page_image_image_2 FOREIGN KEY (image_id) REFERENCES image (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO spex_page_image_new (spex_id, section, kind_id, position, image_id, caption)
SELECT i.spex_id,
       CASE i.section WHEN 'VEST' THEN 'VEST' WHEN 'OTHER' THEN 'OTHER' ELSE 'SCENOGRAPHY' END,
       COALESCE(k.id, 0),
       i.position,
       i.image_id,
       i.caption
FROM spex_page_image i
         LEFT JOIN kind k ON k.uuid = CASE i.section
                                          WHEN 'BACKDROP' THEN '01900000-0000-7000-8000-000000000411'
                                          WHEN 'STAGE' THEN '01900000-0000-7000-8000-000000000412'
                                          WHEN 'PROPS' THEN '01900000-0000-7000-8000-000000000413'
                                          WHEN 'OTHER' THEN '01900000-0000-7000-8000-000000000421' END;

DROP TABLE spex_page_image;
RENAME TABLE spex_page_image_new TO spex_page_image;

-- Spex pages: a short description and facts --------------------------------------------------------------------------

ALTER TABLE spex_page
    ADD COLUMN short_description VARCHAR(1000) NULL AFTER introduction;


CREATE TABLE spex_fact
(
    spex_id  BIGINT        NOT NULL,
    kind_id  BIGINT        NOT NULL,
    value    VARCHAR(1000) NOT NULL,
    PRIMARY KEY (spex_id, kind_id),
    CONSTRAINT fk_spex_fact_spex FOREIGN KEY (spex_id) REFERENCES spex (id) ON DELETE CASCADE,
    CONSTRAINT fk_spex_fact_kind FOREIGN KEY (kind_id) REFERENCES kind (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- The import API: which record each source key made, and what it imported ------------------------------------------

CREATE TABLE import_key
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    source_key     VARCHAR(1000) NOT NULL,
    -- SHA-256 of the source key, for the unique index (source keys may be long)
    source_hash    CHAR(64)      NOT NULL,
    record_kind    VARCHAR(30)   NOT NULL,
    record_id      CHAR(36)      NOT NULL,
    -- SHA-256 of the record as last imported, to tell UNCHANGED from UPDATED
    imported_hash  CHAR(64)      NOT NULL,
    -- The record's version right after the import; a different version later means an editor changed it (KEPT)
    record_version BIGINT        NULL,
    imported_by    VARCHAR(64)   NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_import_key_source UNIQUE (source_hash),
    INDEX idx_import_key_record (record_kind, record_id),
    INDEX idx_import_key_prefix (source_key(255))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
