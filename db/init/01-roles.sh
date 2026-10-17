#!/bin/bash
set -euo pipefail

psql -v ON_ERROR_STOP=1 -v app_password="$APP_PASSWORD" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
-- Групповая роль: только чтение и изменение данных, без права менять структуру БД.
CREATE ROLE podcast_readwrite NOLOGIN;

-- Пользователь приложения: не суперпользователь, не может создавать БД и роли.
CREATE ROLE podcast_app LOGIN PASSWORD :'app_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
GRANT podcast_readwrite TO podcast_app;

-- К БД могут подключаться только владелец и podcast_app.
REVOKE ALL ON DATABASE podcasts FROM PUBLIC;
GRANT CONNECT ON DATABASE podcasts TO podcast_app;
SQL
