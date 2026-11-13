--liquibase formatted sql

--changeset cosmo:001-schema
CREATE SEQUENCE category_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE customer_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE product_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE order_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE order_detail_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE categories (
    category_id BIGINT PRIMARY KEY DEFAULT nextval('category_seq'),
    code VARCHAR(32) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_category_code UNIQUE (code),
    CONSTRAINT ck_category_code CHECK (code IN ('TOYS', 'FOOD', 'ACCESSORIES')),
    CONSTRAINT ck_category_name CHECK (btrim(category_name) <> '')
);
CREATE TABLE customers (
    customer_id BIGINT PRIMARY KEY DEFAULT nextval('customer_seq'),
    customer_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    CONSTRAINT ck_customer_name CHECK (btrim(customer_name) <> ''),
    CONSTRAINT ck_customer_email CHECK (email = lower(btrim(email)) AND email <> '')
);
CREATE TABLE products (
    product_id BIGINT PRIMARY KEY DEFAULT nextval('product_seq'),
    public_id UUID NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    category_id BIGINT NOT NULL REFERENCES categories(category_id),
    seller_email VARCHAR(254) NOT NULL,
    CONSTRAINT uq_product_public_id UNIQUE (public_id),
    CONSTRAINT ck_product_name CHECK (btrim(product_name) <> ''),
    CONSTRAINT ck_product_price CHECK (price > 0),
    CONSTRAINT ck_product_stock CHECK (stock_quantity >= 0)
);
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY DEFAULT nextval('order_seq'),
    order_number VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT ck_order_status CHECK (status IN ('NEW', 'COMPLETED', 'CANCELLED'))
);
CREATE TABLE order_details (
    order_detail_id BIGINT PRIMARY KEY DEFAULT nextval('order_detail_seq'),
    order_id BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(product_id),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    CONSTRAINT uq_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_price CHECK (unit_price > 0)
);
--rollback DROP TABLE order_details;
--rollback DROP TABLE orders;
--rollback DROP TABLE products;
--rollback DROP TABLE customers;
--rollback DROP TABLE categories;
--rollback DROP SEQUENCE order_detail_seq;
--rollback DROP SEQUENCE order_seq;
--rollback DROP SEQUENCE product_seq;
--rollback DROP SEQUENCE customer_seq;
--rollback DROP SEQUENCE category_seq;
