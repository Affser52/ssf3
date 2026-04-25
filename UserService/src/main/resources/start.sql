create extension if not exists pgcrypto;

create table if not exists users (
    id uuid primary key default gen_random_uuid(),
    username varchar(60) not null unique,
    password text not null,
    blocked boolean not null default false,
    avatar_url TEXT not null DEFAULT 'https://90995c79f2f34c065a0d26c1400cc671.bckt.ru/default-avatar/ChatGPT%20Image%2015%20мар.%202026%20г.%2C%2019_46_55.png',
    created_at timestamptz not null default now(),
    online boolean not null default true
);


create table if not exists user_settings (
    user_id uuid primary key,
    auto_delete_message timestamptz,
    allow_writing boolean not null default true,
    allow_add_chat boolean not null default true,
    constraint fk_user_settings_user
        foreign key (user_id)
        references users(id)
        on delete cascade
);

create table if not exists avatar_upload (
    upload_id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    status text not null,
    temp_bucket text not null,
    temp_object_key text not null unique,
    final_bucket text not null,
    final_object_key text not null,
    original_file_name text not null,
    mime_type text not null,
    size_bytes bigint not null,
    upload_url_expires_at timestamptz,
    created_at timestamptz not null default now(),

    constraint fk_avatar_upload_user
        foreign key (user_id)
        references users(id)
        on delete cascade,
    constraint chk_avatar_upload_size_positive
        check (size_bytes > 0)
);

create index if not exists idx_avatar_upload_user_status
    on avatar_upload(user_id, status);

create index if not exists idx_avatar_upload_status_expires
    on avatar_upload(status, upload_url_expires_at);
