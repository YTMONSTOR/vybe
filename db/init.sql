-- Run once as a PostgreSQL superuser if you do not use docker compose:
--   psql -U postgres -f db/init.sql
CREATE USER vybe WITH PASSWORD 'vybe';
CREATE DATABASE vybe OWNER vybe ENCODING 'UTF8';
GRANT ALL PRIVILEGES ON DATABASE vybe TO vybe;
