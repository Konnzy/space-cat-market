--liquibase formatted sql

--changeset cosmo:002-customer-email
ALTER TABLE customers ADD CONSTRAINT uq_customer_email UNIQUE (email);
--rollback ALTER TABLE customers DROP CONSTRAINT uq_customer_email;

--changeset cosmo:003-product-name-in-category
ALTER TABLE products ADD CONSTRAINT uq_product_category_name UNIQUE (category_id, product_name);
--rollback ALTER TABLE products DROP CONSTRAINT uq_product_category_name;

--changeset cosmo:004-order-natural-id
ALTER TABLE orders ADD CONSTRAINT uq_order_number UNIQUE (order_number);
--rollback ALTER TABLE orders DROP CONSTRAINT uq_order_number;

--changeset cosmo:005-search-indexes
CREATE INDEX idx_product_name ON products(product_name);
CREATE INDEX idx_order_customer ON orders(customer_id);
CREATE INDEX idx_order_report ON orders(status, created_at);
CREATE INDEX idx_order_detail_product ON order_details(product_id);
--rollback DROP INDEX idx_order_detail_product;
--rollback DROP INDEX idx_order_report;
--rollback DROP INDEX idx_order_customer;
--rollback DROP INDEX idx_product_name;
