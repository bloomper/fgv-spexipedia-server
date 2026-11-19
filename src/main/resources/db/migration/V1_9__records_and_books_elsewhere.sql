-- Ownership (fgv-infra-deploy docs/data-ownership.md): records are Spexify's albums (its discography) and books are
-- Spexarkivet's; Spexipedia shows them from there (spec 4.4) and keeps none of its own. Nothing is live, so the data
-- goes with them.
DROP TABLE book_song;
DROP TABLE book;
DROP TABLE recording_track;
DROP TABLE recording;

DELETE FROM kind WHERE type IN ('MEDIUM', 'BOOK');
DELETE FROM search_document WHERE kind IN ('RECORDING', 'BOOK');
DELETE FROM import_key WHERE record_kind IN ('RECORDING', 'BOOK');
DELETE FROM redirect WHERE target LIKE '/recordings/%' OR target LIKE '/books/%';
