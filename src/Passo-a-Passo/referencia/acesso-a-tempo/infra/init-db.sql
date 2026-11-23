-- Cria uma base de dados e um utilizador por microserviço.
-- Cada utilizador só consegue ligar-se à SUA base de dados (database per service).

CREATE USER referenciacao_user WITH PASSWORD 'referenciacao_pass';
CREATE DATABASE referenciacao_db OWNER referenciacao_user;
REVOKE CONNECT ON DATABASE referenciacao_db FROM PUBLIC;
GRANT CONNECT ON DATABASE referenciacao_db TO referenciacao_user;

CREATE USER agendamento_user WITH PASSWORD 'agendamento_pass';
CREATE DATABASE agendamento_db OWNER agendamento_user;
REVOKE CONNECT ON DATABASE agendamento_db FROM PUBLIC;
GRANT CONNECT ON DATABASE agendamento_db TO agendamento_user;

CREATE USER lista_espera_user WITH PASSWORD 'lista_espera_pass';
CREATE DATABASE lista_espera_db OWNER lista_espera_user;
REVOKE CONNECT ON DATABASE lista_espera_db FROM PUBLIC;
GRANT CONNECT ON DATABASE lista_espera_db TO lista_espera_user;

CREATE USER tmrg_user WITH PASSWORD 'tmrg_pass';
CREATE DATABASE tmrg_db OWNER tmrg_user;
REVOKE CONNECT ON DATABASE tmrg_db FROM PUBLIC;
GRANT CONNECT ON DATABASE tmrg_db TO tmrg_user;

CREATE USER incumprimento_user WITH PASSWORD 'incumprimento_pass';
CREATE DATABASE incumprimento_db OWNER incumprimento_user;
REVOKE CONNECT ON DATABASE incumprimento_db FROM PUBLIC;
GRANT CONNECT ON DATABASE incumprimento_db TO incumprimento_user;

CREATE USER indicadores_user WITH PASSWORD 'indicadores_pass';
CREATE DATABASE indicadores_db OWNER indicadores_user;
REVOKE CONNECT ON DATABASE indicadores_db FROM PUBLIC;
GRANT CONNECT ON DATABASE indicadores_db TO indicadores_user;

CREATE USER utentes_user WITH PASSWORD 'utentes_pass';
CREATE DATABASE utentes_db OWNER utentes_user;
REVOKE CONNECT ON DATABASE utentes_db FROM PUBLIC;
GRANT CONNECT ON DATABASE utentes_db TO utentes_user;

CREATE USER notificacoes_user WITH PASSWORD 'notificacoes_pass';
CREATE DATABASE notificacoes_db OWNER notificacoes_user;
REVOKE CONNECT ON DATABASE notificacoes_db FROM PUBLIC;
GRANT CONNECT ON DATABASE notificacoes_db TO notificacoes_user;
