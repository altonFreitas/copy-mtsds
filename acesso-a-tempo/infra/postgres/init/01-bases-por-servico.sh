#!/bin/bash
# Uma base de dados e um utilizador por serviço (Database per Service).
# Cada utilizador é dono só da sua base: nenhum serviço consegue ler as tabelas de outro.
# Corre só na primeira vez que o volume pgdata é criado
# (para repetir: docker compose down -v e docker compose up -d).
set -euo pipefail

SERVICOS="referenciacao agendamento lista_espera gestao_acesso garantia_acesso historico utentes notificacoes"

for servico in $SERVICOS; do
    echo "A criar a base ${servico}_db e o utilizador ${servico}"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
         -v utilizador="$servico" -v base="${servico}_db" -v palavra_passe="$SERVICOS_DB_PASSWORD" <<'SQL'
CREATE ROLE :"utilizador" LOGIN PASSWORD :'palavra_passe';
CREATE DATABASE :"base" OWNER :"utilizador";
REVOKE ALL ON DATABASE :"base" FROM PUBLIC;
SQL
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${servico}_db" \
         -v utilizador="$servico" <<'SQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO :"utilizador";
SQL
done
