# MessageService

MessageService отвечает за чаты, сообщения, участников, ключи сообщений, metadata файлов чата и основную бизнес-логику мессенджера.

## Порт

Container port по умолчанию:

```text
8082
```

Обычно сервис не публикуется наружу напрямую. Gateway обращается к нему внутри Docker:

```text
http://message-service:8082
```

## За что отвечает сервис

- Создание личных и групповых чатов.
- Управление участниками чата.
- Сохранение и удаление сообщений.
- Данные для sidebar списка чатов.
- Записи доставки sender keys для сообщений.
- Подсчет использования последнего sender key в чате.
- Загрузка/подтверждение/выдача download URL для файлов чата.
- Очистка временных S3-объектов.
- Публикация Kafka-событий для доставки через Gateway/WebSocket.
- Обработка `user-online` событий для синхронизации данных онлайн-пользователя.

## Модель шифрования сообщений

MessageService не шифрует plaintext сам. Frontend шифрует содержимое сообщения перед отправкой. MessageService сохраняет зашифрованный payload и `encryptionName`.

Sender keys создаются и ротируются на клиенте. Сервер хранит encrypted key envelopes в `send_message_keys`, чтобы получатель мог забрать ключ и расшифровать его своим приватным пользовательским ключом.

## Логика файлов чата

Файлы чата отделены от текстовых сообщений.

Сценарий:

1. Браузер просит у Gateway upload URL, передавая chatId и metadata файла.
2. Gateway пересылает запрос в MessageService.
3. MessageService проверяет доступ к чату и создает S3 POST policy.
4. Браузер загружает файл напрямую в S3.
5. Браузер подтверждает загрузку.
6. MessageService проверяет metadata/размер объекта.
7. MessageService помечает файл как `READY` и публикует событие.
8. Получатели запрашивают временный download URL перед открытием/скачиванием.

Фото, видео и аудио можно показывать inline, если запросить download URL с `inline=true`.

## Основные внутренние API

- чаты: `/api/chats`, `/api/sidebar/chats`, `/api/personal_chat`, `/api/group_chat`;
- участники: `/api/add_user_in_chat`, `/api/delete_user_in_chat`, `/api/block_user`;
- сообщения: `/api/sendMessage`, `/api/messages/{chatId}`, `/api/delete`;
- ключи сообщений: `/api/encrypt_message_key/send`, `/api/encrypt_message_key/pending`, `/api/encrypt_message_key/delete`;
- использование ключа: `/api/key-usage/chats/{chatId}/latest`;
- файлы: `/api/files/upload-url`, `/api/files/{fileId}/complete`, `/api/files/chats/{chatId}`, `/api/files/{fileId}/download-url`;
- вспомогательные endpoints пользователей/чатов, которые использует Gateway.

Публичной точкой входа должен быть Gateway, который передает authenticated user id в MessageService.

## База данных

PostgreSQL + JPA + стартовый SQL.

Основные таблицы:

- `chat` - данные чата.
- `chat_users` - участники и статус пользователя в чате.
- `message` - зашифрованные сообщения.
- `send_message_keys` - зашифрованные sender keys для получателей.
- `users_black_list` - блокировки в чате.
- `chat_files` - metadata файлов и статус загрузки.

## Kafka

Сервис публикует и обрабатывает события, связанные с:

- сообщениями;
- созданием/изменением чатов;
- добавлением/удалением/блокировкой участников;
- доставкой ключей;
- online-синхронизацией пользователя;
- готовностью файла.

Важные topics:

- `message-service`
- `chat-service`
- `encrypt-keys-service`
- `delete-encrypt-keys`
- `user-online`

## S3-настройки

Переменные окружения:

- `S3_ENDPOINT`
- `S3_REGION`
- `S3_ACCESS_KEY`
- `S3_SECRET_KEY`
- `S3_FILES_BUCKET`
- `S3_UPLOAD_URL_TTL`
- `S3_DOWNLOAD_URL_TTL`
- `S3_MAX_FILE_SIZE`
- `S3_CLEANUP_FIXED_DELAY`

## Очистка

`FileStorageCleanupScheduler` периодически удаляет просроченные/зависшие временные S3 upload records и объекты.

## Зависимости

- PostgreSQL message database
- Redis
- Kafka
- UserService
- KeyVaultService
- FirstVDS S3-compatible storage

## Ручной запуск

```powershell
cd MessageService
.\mvnw.cmd -q -DskipTests spring-boot:run
```

Обычно удобнее запускать через Docker Compose:

```powershell
docker compose up -d --build message-service
```