-- Migrazione per aggiungere la colonna "title" alla tabella community_threads
ALTER TABLE community_threads ADD COLUMN title VARCHAR(255) DEFAULT '' NOT NULL;