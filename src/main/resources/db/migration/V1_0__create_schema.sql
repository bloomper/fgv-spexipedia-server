-- Spexipedia's schema. Every entity the API exposes has a UUID (uuid), its id in GraphQL; the BIGINT keys stay
-- internal. Base data keeps the UUIDs it has in the other applications. Nothing here holds a spexare's name: credits
-- and the vest hold Spexregister's spexare UUIDs only.

-- People who have logged in: identity reference, username and display name; deleted 36 months after they were last seen
CREATE TABLE app_user
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    subject      VARCHAR(64)  NOT NULL,
    username     VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NULL,
    created_at   DATETIME(6)  NOT NULL,
    last_seen_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_subject UNIQUE (subject),
    INDEX idx_app_user_last_seen (last_seen_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Uploaded images: the original and its variants are files under images/<uuid>/
CREATE TABLE image
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    uuid               CHAR(36)      NOT NULL,
    kind               VARCHAR(20)   NOT NULL,
    caption            VARCHAR(1000) NULL,
    alt                VARCHAR(1000) NULL,
    original_file_name VARCHAR(255)  NOT NULL,
    content_type       VARCHAR(100)  NOT NULL,
    size_bytes         BIGINT        NOT NULL,
    width              INT           NOT NULL,
    height             INT           NOT NULL,
    sha256             CHAR(64)      NOT NULL,
    -- The widths made, e.g. "320,1280,2560"; empty until the variants exist
    variant_widths     VARCHAR(100)  NOT NULL DEFAULT '',
    trashed_at         DATETIME(6)   NULL,
    created_by         VARCHAR(64)   NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    last_modified_by   VARCHAR(64)   NULL,
    last_modified_at   DATETIME(6)   NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_image_uuid UNIQUE (uuid),
    INDEX idx_image_trashed (trashed_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Base data ----------------------------------------------------------------------------------------------------------

CREATE TABLE spex_category
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    name             VARCHAR(255) NOT NULL,
    first_year       CHAR(4)      NOT NULL,
    description      TEXT         NULL,
    logo_id          BIGINT       NULL,
    retired          BIT          NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_spex_category_uuid UNIQUE (uuid),
    CONSTRAINT fk_spex_category_logo FOREIGN KEY (logo_id) REFERENCES image (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- A revival is a spex with a parent; its title and category are its parent's, so they are null here
CREATE TABLE spex
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    slug             VARCHAR(255) NOT NULL,
    year             CHAR(4)      NOT NULL,
    title            VARCHAR(255) NULL,
    category_id      BIGINT       NULL,
    parent_id        BIGINT       NULL,
    poster_id        BIGINT       NULL,
    retired          BIT          NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_spex_uuid UNIQUE (uuid),
    CONSTRAINT uk_spex_slug UNIQUE (slug),
    CONSTRAINT fk_spex_category FOREIGN KEY (category_id) REFERENCES spex_category (id),
    CONSTRAINT fk_spex_parent FOREIGN KEY (parent_id) REFERENCES spex (id),
    CONSTRAINT fk_spex_poster FOREIGN KEY (poster_id) REFERENCES image (id),
    INDEX idx_spex_year (year)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_category
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    name             VARCHAR(255) NOT NULL,
    actor_present    BIT          NOT NULL DEFAULT 0,
    board            BIT          NOT NULL DEFAULT 0,
    instruments      BIT          NOT NULL DEFAULT 0,
    retired          BIT          NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_category_uuid UNIQUE (uuid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    name             VARCHAR(255) NOT NULL,
    category_id      BIGINT       NOT NULL,
    retired          BIT          NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_uuid UNIQUE (uuid),
    CONSTRAINT fk_task_category FOREIGN KEY (category_id) REFERENCES task_category (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- The ids of merged records, which still resolve to the record they were merged into
CREATE TABLE id_alias
(
    alias     CHAR(36)    NOT NULL,
    kind      VARCHAR(20) NOT NULL,
    target    CHAR(36)    NOT NULL,
    merged_at DATETIME(6) NOT NULL,
    PRIMARY KEY (alias),
    INDEX idx_id_alias_target (target)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- The change feed: every change to base data, characters and numbers, with the record as it was right after it
CREATE TABLE change_feed_entry
(
    sequence       BIGINT      NOT NULL AUTO_INCREMENT,
    kind           VARCHAR(20) NOT NULL,
    record_id      CHAR(36)    NOT NULL,
    change_type    VARCHAR(10) NOT NULL,
    merged_into_id CHAR(36)    NULL,
    payload        JSON        NOT NULL,
    at             DATETIME(6) NOT NULL,
    PRIMARY KEY (sequence)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sync_consumer
(
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)      NOT NULL,
    name              VARCHAR(255)  NOT NULL,
    client_id         VARCHAR(255)  NOT NULL,
    notify_url        VARCHAR(1000) NULL,
    enabled           BIT           NOT NULL DEFAULT 1,
    acknowledged      BIGINT        NULL,
    acknowledged_at   DATETIME(6)   NULL,
    -- The sequence the consumer was last told about, so that failed notifications are sent again
    notified          BIGINT        NULL,
    last_notified_at  DATETIME(6)   NULL,
    last_error        VARCHAR(1000) NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sync_consumer_uuid UNIQUE (uuid),
    CONSTRAINT uk_sync_consumer_client UNIQUE (client_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- What makes up a spex ------------------------------------------------------------------------------------------------

CREATE TABLE figure
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    slug             VARCHAR(255) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      TEXT         NULL,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_figure_uuid UNIQUE (uuid),
    CONSTRAINT uk_figure_slug UNIQUE (slug)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE spex_character
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    spex_id          BIGINT       NOT NULL,
    name             VARCHAR(255) NOT NULL,
    figure_id        BIGINT       NULL,
    description      TEXT         NULL,
    position         INT          NOT NULL,
    retired          BIT          NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_spex_character_uuid UNIQUE (uuid),
    CONSTRAINT fk_spex_character_spex FOREIGN KEY (spex_id) REFERENCES spex (id),
    CONSTRAINT fk_spex_character_figure FOREIGN KEY (figure_id) REFERENCES figure (id),
    INDEX idx_spex_character_spex (spex_id, position)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE character_image
(
    character_id BIGINT        NOT NULL,
    position     INT           NOT NULL,
    image_id     BIGINT        NOT NULL,
    kind         VARCHAR(20)   NOT NULL,
    caption      VARCHAR(1000) NULL,
    PRIMARY KEY (character_id, position),
    CONSTRAINT fk_character_image_character FOREIGN KEY (character_id) REFERENCES spex_character (id) ON DELETE CASCADE,
    CONSTRAINT fk_character_image_image FOREIGN KEY (image_id) REFERENCES image (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE scene
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    spex_id          BIGINT       NOT NULL,
    act              INT          NOT NULL,
    position         INT          NOT NULL,
    title            VARCHAR(255) NOT NULL,
    place            VARCHAR(255) NULL,
    interval_after   BIT          NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_scene_uuid UNIQUE (uuid),
    CONSTRAINT fk_scene_spex FOREIGN KEY (spex_id) REFERENCES spex (id),
    INDEX idx_scene_spex (spex_id, act, position)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE spex_number
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    spex_id          BIGINT       NOT NULL,
    act              INT          NOT NULL,
    position         INT          NOT NULL,
    title            VARCHAR(255) NOT NULL,
    original_title   VARCHAR(255) NULL,
    original_artist  VARCHAR(255) NULL,
    retired          BIT          NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_spex_number_uuid UNIQUE (uuid),
    CONSTRAINT fk_spex_number_spex FOREIGN KEY (spex_id) REFERENCES spex (id),
    INDEX idx_spex_number_spex (spex_id, act, position)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- A credit is a spexare in Spexregister (spexare_id) or someone outside FGV by name, never both
CREATE TABLE number_credit
(
    number_id  BIGINT       NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    position   INT          NOT NULL,
    spexare_id CHAR(36)     NULL,
    name       VARCHAR(255) NULL,
    PRIMARY KEY (number_id, kind, position),
    CONSTRAINT fk_number_credit_number FOREIGN KEY (number_id) REFERENCES spex_number (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE number_character
(
    number_id    BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    PRIMARY KEY (number_id, character_id),
    CONSTRAINT fk_number_character_number FOREIGN KEY (number_id) REFERENCES spex_number (id) ON DELETE CASCADE,
    CONSTRAINT fk_number_character_character FOREIGN KEY (character_id) REFERENCES spex_character (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- A revival's changes to its parent's characters, scenes and numbers: cut, or moved
CREATE TABLE revival_change
(
    revival_id BIGINT      NOT NULL,
    kind       VARCHAR(20) NOT NULL,
    record_id  CHAR(36)    NOT NULL,
    cut        BIT         NOT NULL DEFAULT 0,
    act        INT         NULL,
    position   INT         NULL,
    PRIMARY KEY (revival_id, kind, record_id),
    CONSTRAINT fk_revival_change_revival FOREIGN KEY (revival_id) REFERENCES spex (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE recording
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                CHAR(36)     NOT NULL,
    slug                VARCHAR(255) NOT NULL,
    catalogue_number    VARCHAR(50)  NOT NULL,
    title               VARCHAR(255) NOT NULL,
    medium              VARCHAR(10)  NOT NULL,
    spex_id             BIGINT       NULL,
    released            VARCHAR(10)  NULL,
    recorded            VARCHAR(10)  NULL,
    conductor_spexare_id CHAR(36)    NULL,
    conductor_name      VARCHAR(255) NULL,
    description         TEXT         NULL,
    front_image_id      BIGINT       NULL,
    back_image_id       BIGINT       NULL,
    spexify_album_id    CHAR(36)     NULL,
    published           BIT          NOT NULL DEFAULT 0,
    created_by          VARCHAR(64)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    last_modified_by    VARCHAR(64)  NULL,
    last_modified_at    DATETIME(6)  NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_recording_uuid UNIQUE (uuid),
    CONSTRAINT uk_recording_slug UNIQUE (slug),
    CONSTRAINT fk_recording_spex FOREIGN KEY (spex_id) REFERENCES spex (id),
    CONSTRAINT fk_recording_front FOREIGN KEY (front_image_id) REFERENCES image (id),
    CONSTRAINT fk_recording_back FOREIGN KEY (back_image_id) REFERENCES image (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE recording_track
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    uuid         CHAR(36)     NOT NULL,
    recording_id BIGINT       NOT NULL,
    side         VARCHAR(10)  NULL,
    position     INT          NOT NULL,
    number_id    BIGINT       NULL,
    title        VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recording_track_uuid UNIQUE (uuid),
    CONSTRAINT fk_recording_track_recording FOREIGN KEY (recording_id) REFERENCES recording (id) ON DELETE CASCADE,
    CONSTRAINT fk_recording_track_number FOREIGN KEY (number_id) REFERENCES spex_number (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- The spex page ------------------------------------------------------------------------------------------------------

CREATE TABLE spex_page
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    spex_id               BIGINT       NOT NULL,
    generation            VARCHAR(255) NULL,
    premiere              DATE         NULL,
    introduction          TEXT         NULL,
    description           MEDIUMTEXT   NULL,
    synopsis              MEDIUMTEXT   NULL,
    rebus_image_id        BIGINT       NULL,
    rebus_solution_id     BIGINT       NULL,
    vest_description      TEXT         NULL,
    vest_worn_by_spexare  CHAR(36)     NULL,
    vest_worn_by_name     VARCHAR(255) NULL,
    published             BIT          NOT NULL DEFAULT 0,
    created_by            VARCHAR(64)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    last_modified_by      VARCHAR(64)  NULL,
    last_modified_at      DATETIME(6)  NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_spex_page_spex UNIQUE (spex_id),
    CONSTRAINT fk_spex_page_spex FOREIGN KEY (spex_id) REFERENCES spex (id),
    CONSTRAINT fk_spex_page_rebus FOREIGN KEY (rebus_image_id) REFERENCES image (id),
    CONSTRAINT fk_spex_page_solution FOREIGN KEY (rebus_solution_id) REFERENCES image (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE spex_page_image
(
    spex_id  BIGINT        NOT NULL,
    section  VARCHAR(20)   NOT NULL,
    position INT           NOT NULL,
    image_id BIGINT        NOT NULL,
    caption  VARCHAR(1000) NULL,
    PRIMARY KEY (spex_id, section, position),
    CONSTRAINT fk_spex_page_image_spex FOREIGN KEY (spex_id) REFERENCES spex (id) ON DELETE CASCADE,
    CONSTRAINT fk_spex_page_image_image FOREIGN KEY (image_id) REFERENCES image (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Ids of items in Spexgram picked for the page; Spexgram decides who sees them
CREATE TABLE spex_page_photo
(
    spex_id      BIGINT      NOT NULL,
    section      VARCHAR(20) NOT NULL,
    character_id BIGINT      NOT NULL DEFAULT 0,
    position     INT         NOT NULL,
    item_id      CHAR(36)    NOT NULL,
    PRIMARY KEY (spex_id, section, character_id, position),
    CONSTRAINT fk_spex_page_photo_spex FOREIGN KEY (spex_id) REFERENCES spex (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Articles -----------------------------------------------------------------------------------------------------------

CREATE TABLE article_category
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    slug             VARCHAR(255) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      TEXT         NULL,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_article_category_uuid UNIQUE (uuid),
    CONSTRAINT uk_article_category_slug UNIQUE (slug)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE article
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    slug             VARCHAR(255) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    body             MEDIUMTEXT   NOT NULL,
    visibility       VARCHAR(10)  NOT NULL,
    published        BIT          NOT NULL DEFAULT 0,
    trashed_at       DATETIME(6)  NULL,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_article_uuid UNIQUE (uuid),
    CONSTRAINT uk_article_slug UNIQUE (slug)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE article_article_category
(
    article_id  BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (article_id, category_id),
    CONSTRAINT fk_aac_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_aac_category FOREIGN KEY (category_id) REFERENCES article_category (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Who is editing an article now
CREATE TABLE article_editing
(
    article_id BIGINT      NOT NULL,
    subject    VARCHAR(64) NOT NULL,
    since      DATETIME(6) NOT NULL,
    PRIMARY KEY (article_id, subject),
    CONSTRAINT fk_article_editing_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Every save of an article, and of a spex page's texts (field INTRODUCTION, DESCRIPTION or SYNOPSIS)
CREATE TABLE revision
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    uuid         CHAR(36)      NOT NULL,
    target       VARCHAR(20)   NOT NULL,
    target_id    CHAR(36)      NOT NULL,
    field        VARCHAR(20)   NULL,
    title        VARCHAR(255)  NOT NULL,
    body         MEDIUMTEXT    NOT NULL,
    previous_id  BIGINT        NULL,
    comment      VARCHAR(500)  NULL,
    hidden       BIT           NOT NULL DEFAULT 0,
    hidden_by    VARCHAR(64)   NULL,
    author       VARCHAR(64)   NOT NULL,
    author_name  VARCHAR(255)  NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_revision_uuid UNIQUE (uuid),
    INDEX idx_revision_target (target, target_id, field, id),
    INDEX idx_revision_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Old MediaWiki titles, normalised (lower case, spaces for underscores), and the path they lead to
CREATE TABLE redirect
(
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)      NOT NULL,
    old_title        VARCHAR(255)  NOT NULL,
    normalized_title VARCHAR(255)  NOT NULL,
    target           VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_redirect_uuid UNIQUE (uuid),
    CONSTRAINT uk_redirect_title UNIQUE (normalized_title)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- The search index: one row per searchable record, rebuilt by a maintenance job. Never holds a person's name
CREATE TABLE search_document
(
    kind        VARCHAR(20)   NOT NULL,
    record_id   CHAR(36)      NOT NULL,
    title       VARCHAR(500)  NOT NULL,
    context     VARCHAR(500)  NULL,
    body        MEDIUMTEXT    NULL,
    path        VARCHAR(1000) NOT NULL,
    -- PUBLIC, MEMBERS or HIDDEN (unpublished: only for those who may see unpublished pages)
    visibility  VARCHAR(10)   NOT NULL,
    PRIMARY KEY (kind, record_id),
    FULLTEXT INDEX ftx_search_document (title, context, body)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Administration -----------------------------------------------------------------------------------------------------

CREATE TABLE settings
(
    id                BIGINT      NOT NULL,
    welcome_sv        TEXT        NULL,
    welcome_en        TEXT        NULL,
    public_welcome_sv TEXT        NULL,
    public_welcome_en TEXT        NULL,
    contact_email     VARCHAR(255) NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    last_modified_by  VARCHAR(64) NULL,
    last_modified_at  DATETIME(6) NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO settings (id) VALUES (1);

CREATE TABLE audit_entry
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    at            DATETIME(6)   NOT NULL,
    actor_subject VARCHAR(64)   NOT NULL,
    actor_name    VARCHAR(255)  NULL,
    action        VARCHAR(40)   NOT NULL,
    entity_type   VARCHAR(30)   NOT NULL,
    entity_id     CHAR(36)      NULL,
    summary       VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_audit_entry_at (at),
    INDEX idx_audit_entry_actor (actor_subject, at),
    INDEX idx_audit_entry_entity (entity_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
