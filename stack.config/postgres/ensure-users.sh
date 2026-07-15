#!/bin/bash
set -e
until pg_isready -U "${POSTGRES_USER}"; do
    echo "Waiting for PostgreSQL to be ready..."
    sleep 2
done
echo "PostgreSQL is ready. Ensuring users and databases..."
POSTGRES_PLANKA_PASSWORD="${POSTGRES_PLANKA_PASSWORD:?ERROR: POSTGRES_PLANKA_PASSWORD not set}"
POSTGRES_SYNAPSE_PASSWORD="${POSTGRES_SYNAPSE_PASSWORD:?ERROR: POSTGRES_SYNAPSE_PASSWORD not set}"
MARIADB_BOOKSTACK_PASSWORD="${MARIADB_BOOKSTACK_PASSWORD:?ERROR: MARIADB_BOOKSTACK_PASSWORD not set}"
psql -v ON_ERROR_STOP=1 \
    -v POSTGRES_PLANKA_PASSWORD="$POSTGRES_PLANKA_PASSWORD" \
    -v POSTGRES_SYNAPSE_PASSWORD="$POSTGRES_SYNAPSE_PASSWORD" \
    -v MARIADB_BOOKSTACK_PASSWORD="$MARIADB_BOOKSTACK_PASSWORD" \
    --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-'EOSQL'
    -- Create or update users with passwords
    SELECT format('CREATE USER planka WITH PASSWORD %L', :'POSTGRES_PLANKA_PASSWORD')
    WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'planka')\gexec
    SELECT format('ALTER USER planka WITH PASSWORD %L', :'POSTGRES_PLANKA_PASSWORD')\gexec
    SELECT format('CREATE USER synapse WITH PASSWORD %L', :'POSTGRES_SYNAPSE_PASSWORD')
    WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'synapse')\gexec
    SELECT format('ALTER USER synapse WITH PASSWORD %L', :'POSTGRES_SYNAPSE_PASSWORD')\gexec
    SELECT format('CREATE USER bookstack WITH PASSWORD %L', :'MARIADB_BOOKSTACK_PASSWORD')
    WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'bookstack')\gexec
    SELECT format('ALTER USER bookstack WITH PASSWORD %L', :'MARIADB_BOOKSTACK_PASSWORD')\gexec
    -- Create databases if they don't exist
    SELECT 'CREATE DATABASE planka OWNER planka'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'planka')\gexec
    SELECT 'CREATE DATABASE langgraph OWNER postgres'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'langgraph')\gexec
    SELECT 'CREATE DATABASE synapse OWNER synapse'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'synapse')\gexec
    SELECT 'CREATE DATABASE bookstack OWNER bookstack'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bookstack')\gexec
    -- Grant privileges (idempotent)
    GRANT ALL PRIVILEGES ON DATABASE planka TO planka;
    GRANT ALL PRIVILEGES ON DATABASE langgraph TO postgres;
    GRANT ALL PRIVILEGES ON DATABASE synapse TO synapse;
    GRANT ALL PRIVILEGES ON DATABASE bookstack TO bookstack;
EOSQL
for db in planka synapse bookstack; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" -c "GRANT ALL ON SCHEMA public TO $db;" 2>/dev/null || true
done

echo "✅ PostgreSQL users and databases verified"
