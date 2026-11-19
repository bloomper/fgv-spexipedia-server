-- Figures go: a role (character) belongs to one spex, and the few that recur across spex do not need a register of their
-- own (an article covers them). Nothing is live, so the data goes with them.
ALTER TABLE spex_character DROP FOREIGN KEY fk_spex_character_figure;
ALTER TABLE spex_character DROP COLUMN figure_id;
DROP TABLE figure;
DELETE FROM search_document WHERE kind = 'FIGURE';
DELETE FROM import_key WHERE record_kind = 'FIGURE';
