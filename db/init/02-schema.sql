
CREATE TABLE users (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    email       VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_username_not_blank CHECK (btrim(username) <> ''),
    CONSTRAINT users_email_format CHECK (email LIKE '_%@_%')
);

CREATE UNIQUE INDEX users_username_key ON users (lower(username));
CREATE UNIQUE INDEX users_email_key    ON users (lower(email));

CREATE TABLE podcasts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    author_id   BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT podcasts_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT podcasts_status_valid CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);
CREATE INDEX podcasts_author_idx ON podcasts (author_id);

CREATE TABLE episodes (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    podcast_id       BIGINT       NOT NULL REFERENCES podcasts (id) ON DELETE CASCADE,
    title            VARCHAR(200) NOT NULL,
    duration_seconds INTEGER      NOT NULL,
    state            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT episodes_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT episodes_duration_positive CHECK (duration_seconds > 0),
    CONSTRAINT episodes_state_valid CHECK (state IN ('DRAFT', 'SCHEDULED', 'PUBLISHED')),

    CONSTRAINT episodes_id_podcast_key UNIQUE (id, podcast_id)
);
CREATE INDEX episodes_podcast_idx ON episodes (podcast_id);

CREATE TABLE audio_files (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    episode_id  BIGINT       NOT NULL REFERENCES episodes (id) ON DELETE CASCADE,
    path        VARCHAR(255) NOT NULL,
    format      VARCHAR(10)  NOT NULL,
    size_bytes  BIGINT       NOT NULL,
    is_main     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT audio_files_format_valid CHECK (format IN ('MP3', 'WAV', 'OGG', 'FLAC', 'AAC')),
    CONSTRAINT audio_files_size_positive CHECK (size_bytes > 0),

    CONSTRAINT audio_files_path_safe CHECK (path !~ '\.\.' AND path !~ '^/' AND btrim(path) <> ''),
    CONSTRAINT audio_files_episode_path_key UNIQUE (episode_id, path)
);
CREATE INDEX audio_files_episode_idx ON audio_files (episode_id);

CREATE UNIQUE INDEX audio_files_one_main_per_episode ON audio_files (episode_id) WHERE is_main;

CREATE TABLE publication_slots (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    podcast_id    BIGINT      NOT NULL REFERENCES podcasts (id) ON DELETE CASCADE,
    episode_id    BIGINT      NOT NULL,
    scheduled_at  TIMESTAMPTZ NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT slots_status_valid CHECK (status IN ('PLANNED', 'PUBLISHED', 'CANCELLED')),

    CONSTRAINT slots_published_at_consistent CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL)),

    CONSTRAINT slots_episode_in_podcast_fk FOREIGN KEY (episode_id, podcast_id)
        REFERENCES episodes (id, podcast_id) ON DELETE CASCADE
);
CREATE INDEX slots_episode_idx ON publication_slots (episode_id);
CREATE INDEX slots_podcast_idx ON publication_slots (podcast_id);

CREATE UNIQUE INDEX slots_one_planned_per_episode   ON publication_slots (episode_id) WHERE status = 'PLANNED';
CREATE UNIQUE INDEX slots_one_published_per_episode ON publication_slots (episode_id) WHERE status = 'PUBLISHED';

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO podcast_readwrite;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO podcast_readwrite;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO podcast_readwrite;
