create extension if not exists pgcrypto;

CREATE TABLE IF NOT EXISTS chat (
    chat_id BIGSERIAL PRIMARY KEY,
    chat_type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    title TEXT,
    avatar_url TEXT not null DEFAULT 'https://90995c79f2f34c065a0d26c1400cc671.bckt.ru/default-avatar/ChatGPT%20Image%2015%20мар.%202026%20г.%2C%2019_46_55.png'
);

CREATE TABLE IF NOT EXISTS  chat_users (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    user_id UUID NOT NULL,
    role TEXT,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_chat_users_chat_user UNIQUE (chat_id, user_id),
    CONSTRAINT fk_chat_users_chat
        FOREIGN KEY (chat_id)
        REFERENCES chat(chat_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS  message (
    message_id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    message BYTEA NOT NULL,
    encryption_name TEXT NOT NULL,
    sender_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_message_chat
        FOREIGN KEY (chat_id)
        REFERENCES chat(chat_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_file (
    file_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status TEXT NOT NULL,
    sender_id UUID NOT NULL,
    chat_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    object_key TEXT NOT NULL UNIQUE,
    upload_object_key TEXT NOT NULL UNIQUE,
    upload_url_expires_at TIMESTAMPTZ,
    file_type TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    original_file_name TEXT NOT NULL,

    CONSTRAINT fk_chat_file_chat
        FOREIGN KEY (chat_id)
        REFERENCES chat(chat_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_chat_file_size_positive
        CHECK (size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_chat_file_chat_created_at
    ON chat_file(chat_id, created_at);

CREATE INDEX IF NOT EXISTS idx_chat_file_status
    ON chat_file(status);

ALTER TABLE chat_file
    ADD COLUMN IF NOT EXISTS upload_object_key TEXT;

ALTER TABLE chat_file
    ADD COLUMN IF NOT EXISTS upload_url_expires_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_file_upload_object_key
    ON chat_file(upload_object_key)
    WHERE upload_object_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_file_status_upload_expires
    ON chat_file(status, upload_url_expires_at);

CREATE TABLE IF NOT EXISTS send_message_keys (
      id UUID PRIMARY KEY default gen_random_uuid(),
      encrypt_name TEXT NOT NULL,
      chat_id BIGINT,
      user_id UUID NOT NULL,
      key BYTEA NOT NULL,
      public_key UUID NOT NULL,
      user_target_id UUID,
      send_at TIMESTAMPTZ NOT NULL DEFAULT now()
  );

CREATE TABLE IF NOT EXISTS  users_black_list(
    id BIGSERIAL PRIMARY KEY,
    user_initiator UUID NOT NULL,
    chat_id BIGINT NOT NULL,
    user_target UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_users_black_list_chat
        FOREIGN KEY (chat_id)
        REFERENCES chat(chat_id)
        ON DELETE CASCADE
)
