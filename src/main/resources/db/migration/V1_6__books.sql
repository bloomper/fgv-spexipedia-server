-- Books: anything printed, e.g. the books (Chalmersspex I, II …) of the spex' songs. Those were recordings of the
-- medium Spexbok; a book is not a recording, so books are records of their own, their songs the spex' numbers.
CREATE TABLE book
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)     NOT NULL,
    slug             VARCHAR(255) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    -- Its number in the series: 1 for Chalmersspex I
    volume           INT          NULL,
    published_year   VARCHAR(10)  NULL,
    description      TEXT         NULL,
    cover_image_id   BIGINT       NULL,
    published        BIT          NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_modified_by VARCHAR(64)  NULL,
    last_modified_at DATETIME(6)  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_book_uuid UNIQUE (uuid),
    CONSTRAINT uk_book_slug UNIQUE (slug),
    CONSTRAINT fk_book_cover FOREIGN KEY (cover_image_id) REFERENCES image (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- A song of a book: a number of a spex, or a free title; text_only when the book has only the words
CREATE TABLE book_song
(
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    uuid      CHAR(36)     NOT NULL,
    book_id   BIGINT       NOT NULL,
    position  INT          NOT NULL,
    number_id BIGINT       NULL,
    title     VARCHAR(255) NULL,
    text_only BIT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_book_song_uuid UNIQUE (uuid),
    CONSTRAINT fk_book_song_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE,
    CONSTRAINT fk_book_song_number FOREIGN KEY (number_id) REFERENCES spex_number (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_book_song_number ON book_song (number_id);

-- The medium Spexbok goes, with the recordings made of it (nothing is live; the import makes them as books)
DELETE FROM recording WHERE medium_id = (SELECT id FROM (SELECT id FROM kind WHERE uuid = '01900000-0000-7000-8000-000000000306') k);
DELETE FROM kind WHERE uuid = '01900000-0000-7000-8000-000000000306';
UPDATE kind SET position = 5 WHERE uuid = '01900000-0000-7000-8000-000000000305';
