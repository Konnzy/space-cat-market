--liquibase formatted sql

--changeset cosmo:006-default-categories
INSERT INTO categories (code, category_name) VALUES ('TOYS', 'Toys'), ('FOOD', 'Food'), ('ACCESSORIES', 'Accessories');
--rollback DELETE FROM categories WHERE code IN ('TOYS', 'FOOD', 'ACCESSORIES');
