SELECT 'CREATE DATABASE camunda' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'camunda')\gexec
