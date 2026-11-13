--liquibase formatted sql

--changeset cosmo:007-archive-and-catalog-indexes
ALTER TABLE products ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_product_active_id ON products(active, product_id);
CREATE INDEX idx_product_active_price ON products(active, price, product_id);
CREATE INDEX idx_product_active_name ON products(active, product_name, product_id);
--rollback DROP INDEX idx_product_active_name;
--rollback DROP INDEX idx_product_active_price;
--rollback DROP INDEX idx_product_active_id;
--rollback ALTER TABLE products DROP COLUMN active;
