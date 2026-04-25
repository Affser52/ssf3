# Gateway

Gateway - публичная точка входа в Mescat.

Он выполняет три основные роли:

- отдает страницы авторизации, регистрации и чата;
- предоставляет frontend-facing API для браузера;
- слушает Kafka-события и доставляет их подключенным пользователям/чатам через WebSocket.

## Порт

Локальный/container port по умолчанию:

```text
8080
```

Основные URL:

```text
http://localhost:8080
http://localhost:8080/chat.html
```

## За что отвечает сервис

- Страницы входа и регистрации.
- Security/session flow.
- Статические frontend-файлы из `src/main/resources/static`.
- Проксирование API-запросов к `UserService`, `MessageService` и key endpoints.
- Получение текущего userId из Spring Security username.
- Отправка события `user-online` при подключении пользователя.
- Kafka listeners для backend-событий.
- WebSocket-доставка событий в `/user/queue/events` и chat topics.

## Основные frontend-файлы

- `src/main/resources/static/chat.html` - HTML страницы чата.
- `src/main/resources/static/chat.css` - стили desktop/mobile интерфейса.
- `src/main/resources/static/js/chat/app.js` - точка сборки frontend-логики.
- `src/main/resources/static/js/chat/api.js` - HTTP API client.
- `src/main/resources/static/js/chat/chat-manager.js` - чаты, сообщения, файлы.
- `src/main/resources/static/js/chat/key-manager.js` - пользовательские ключи и sender keys.
- `src/main/resources/static/js/chat/settings-manager.js` - настройки пользователя.
- `src/main/resources/static/js/chat/file-uploader.js` - прямая загрузка файлов в S3.
- `src/main/resources/static/js/chat/ws.js` - WebSocket client.

## Важное замечание про frontend

Frontend был создан при помощи нейронки и требует ревью/рефакторинга перед production. Backend API contracts важнее как источник истины.

## Основные группы API

Gateway открывает браузеру endpoints `/api/...` и в основном проксирует их в backend:

- auth/session: `/auth/...`, `/api/getId`;
- чаты: `/api/chats`, `/api/sidebar/chats`, `/api/personal_chat`, `/api/group_chat`;
- сообщения: `/api/sendMessage`, `/api/messages/{chatId}`, `/api/delete`;
- участники: `/api/add_user_in_chat`, `/api/delete_user_in_chat`, `/api/block_user`;
- ключи сообщений: `/api/encrypt_message_key/...`;
- пользовательские ключи: `/api/encrypt_key/...`;
- файлы: `/api/files/...`;
- настройки: `/api/settings/...`;
- поиск: `/api/search_by_username/{username}`.

## WebSocket

Браузер подключается к:

```text
/ws
```

Frontend слушает:

```text
/user/queue/events
/topic/chat/{chatId}
```

## Зависимости

- `UserService`
- `MessageService`
- Kafka
- Redis

## Переменные окружения

Обычно задаются через корневой `docker-compose.yml`:

- `SERVER_PORT`
- `REDIS_HOST`
- `REDIS_PORT`
- `KAFKA_BOOTSTRAP_SERVERS`
- `USER_SERVICE_URL`
- `MESSAGE_SERVICE_URL`
- JWT-настройки из YAML/env defaults

## Ручной запуск

```powershell
cd Gateway
.\mvnw.cmd -q -DskipTests spring-boot:run
```

Обычно удобнее запускать через Docker Compose:

```powershell
docker compose up -d --build gateway
```

## Принцип разработки

Gateway должен оставаться простым: авторизовать пользователя, брать userId из Spring Security, проксировать запросы в нужный сервис и доставлять события в браузер. Бизнес-логика должна жить в backend-сервисах.