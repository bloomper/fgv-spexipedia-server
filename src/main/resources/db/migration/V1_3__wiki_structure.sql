-- Articles as a wiki (spec 4.5): a tree of articles, nested categories, and the links between articles

-- A tree of articles: a parent article and a position among its siblings
ALTER TABLE article
    ADD COLUMN parent_id BIGINT NULL AFTER slug,
    ADD COLUMN position  INT    NOT NULL DEFAULT 0 AFTER parent_id,
    ADD CONSTRAINT fk_article_parent FOREIGN KEY (parent_id) REFERENCES article (id),
    ADD INDEX idx_article_parent (parent_id, position);

-- Nested categories: a category may belong to one or more parent categories, as in the MediaWiki
CREATE TABLE article_category_parent
(
    category_id BIGINT NOT NULL,
    parent_id   BIGINT NOT NULL,
    PRIMARY KEY (category_id, parent_id),
    CONSTRAINT fk_acp_category FOREIGN KEY (category_id) REFERENCES article_category (id) ON DELETE CASCADE,
    CONSTRAINT fk_acp_parent FOREIGN KEY (parent_id) REFERENCES article_category (id) ON DELETE CASCADE,
    INDEX idx_acp_parent (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- The [[links]] in each article's latest text, for What links here and wanted articles. to_article_id is null while
-- no article has the title; normalized_title is the title in lower case with spaces for underscores.
CREATE TABLE article_link
(
    from_article_id  BIGINT       NOT NULL,
    normalized_title VARCHAR(255) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    to_article_id    BIGINT       NULL,
    PRIMARY KEY (from_article_id, normalized_title),
    CONSTRAINT fk_article_link_from FOREIGN KEY (from_article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_link_to FOREIGN KEY (to_article_id) REFERENCES article (id) ON DELETE SET NULL,
    INDEX idx_article_link_title (normalized_title),
    INDEX idx_article_link_to (to_article_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Spex packages (spec 5.14): one row per uploaded package; its files live in imports/packages/<uuid>/
CREATE TABLE spex_package
(
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)      NOT NULL,
    -- UPLOADED, PREVIEWED, APPLYING, APPLIED, FAILED
    state            VARCHAR(20)   NOT NULL,
    file_name        VARCHAR(255)  NOT NULL,
    zip_file_name    VARCHAR(255)  NULL,
    spex_id          CHAR(36)      NULL,
    -- The preview and the results per application, as JSON
    preview          MEDIUMTEXT    NULL,
    results          MEDIUMTEXT    NULL,
    progress         INT           NOT NULL DEFAULT 0,
    message          VARCHAR(1000) NULL,
    uploaded_by      VARCHAR(64)   NOT NULL,
    uploaded_by_name VARCHAR(255)  NULL,
    applied_at       DATETIME(6)   NULL,
    created_by       VARCHAR(64)   NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    last_modified_by VARCHAR(64)   NULL,
    last_modified_at DATETIME(6)   NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_spex_package_uuid UNIQUE (uuid),
    INDEX idx_spex_package_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
