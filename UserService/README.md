# UserService

UserService отвечает за пользователей, профиль, настройки пользователя и загрузку аватарок.

## Порт

Container port по умолчанию:

```text
8081
```

Обычно сервис не публикуется наружу напрямую. Gateway обращается к нему внутри Docker:

```text
http://user-service:8081
```

## За что отвечает сервис

- Регистрация и поиск пользователей.
- Изменение username и password.
- Настройки пользователя: разрешено ли писать лично, разрешено ли добавлять в чат.
- Профильные данные для чатов и sidebar.
- Жизненный цикл загрузки аватарок.
- Сохранение публичного `avatarUrl` в профиле пользователя.

## Логика аватарок

Аватарки не являются chat file entity.

Сценарий:

1. Браузер просит у Gateway ссылку для загрузки аватарки.
2. Gateway пересылает запрос в `UserService`.
3. `UserService` создает временную S3 POST policy для изображения.
4. Браузер загружает файл напрямую в S3.
5. Браузер подтверждает завершение загрузки через Gateway/UserService.
6. `UserService` проверяет metadata и magic bytes изображения.
7. `UserService` копирует объект в публичный avatars bucket с `public-read`.
8. `UserService` сохраняет публичный `avatarUrl` в таблицу пользователя.
9. Браузер и другие пользователи показывают аватарку через обычный `<img src="avatarUrl">`.

Рекомендуемые buckets:

- временная загрузка аватарки: `mescat-files` или другой приватный/temp bucket;
- финальные аватарки: `mescat-avatars`, публичное чтение.

## Основные внутренние API

- `POST /auth/reg`
- `POST /auth/login`
- `GET /auth/info/{username}`
- `GET /auth/info/id/{id}`
- `PATCH /user/{id}/username`
- `PATCH /user/{id}/password`
- `PATCH /user/{id}/avatar-url`
- `GET /user/search/contains/{username}`
- `GET /user_settings/{id}`
- `PATCH /user_settings/{id}/allow-writing`
- `PATCH /user_settings/{id}/allow-add-chat`
- `POST /user/{userId}/avatar/upload-url`
- `POST /user/{userId}/avatar/{uploadId}/complete`

Публичной точкой входа должен быть Gateway. В production не стоит открывать UserService напрямую наружу.

## База данных

PostgreSQL + JPA + стартовый SQL.

Основные таблицы:

- `users` - учетные записи и профиль.
- `user_settings` - настройки поведения/приватности.
- `avatar_uploads` - состояние временной загрузки аватарок.

## S3-настройки

Переменные окружения:

- `S3_ENDPOINT`
- `S3_REGION`
- `S3_ACCESS_KEY`
- `S3_SECRET_KEY`
- `S3_AVATARS_BUCKET`
- `S3_FILES_BUCKET`
- `S3_AVATAR_TEMP_BUCKET`
- `S3_UPLOAD_URL_TTL`
- `S3_DOWNLOAD_URL_TTL`
- `S3_AVATAR_MAX_FILE_SIZE`
- `S3_AVATAR_CLEANUP_FIXED_DELAY`

## Очистка

`AvatarCleanupScheduler` периодически удаляет просроченные временные загрузки аватарок и помечает старые записи как expired.

## Ручной запуск

```powershell
cd UserService
.\mvnw.cmd -q -DskipTests spring-boot:run
```

Обычно удобнее запускать через Docker Compose:

```powershell
docker compose up -d --build user-service
```