# KeyVaultService

KeyVaultService хранит публичные ключи пользователей и encrypted handoff-записи для передачи новых приватных ключей между сессиями/устройствами пользователя.

## Порт

Container port по умолчанию:

```text
8085
```

Обычно сервис не публикуется наружу напрямую. MessageService/Gateway обращаются к нему внутри Docker:

```text
http://key-vault-service:8085
```

## За что отвечает сервис

- Хранение одного активного публичного ключа пользователя.
- Получение публичных ключей по userId или keyId.
- Хранение зашифрованных записей новых приватных ключей для других сессий/устройств пользователя.
- Выдача pending private-key records целевому пользователю.
- Удаление/потребление handoff-записей, когда они больше не нужны.

## Модель пользовательских ключей

Backend не должен знать plaintext приватный ключ.

Frontend локально владеет приватными ключами пользователя. Публичные ключи загружаются в KeyVaultService. Когда пользователь создает или ротирует пару ключей, encrypted private-key handoff records могут сохраняться в базе, чтобы другая сессия пользователя смогла восстановить актуальную цепочку приватных ключей.

## Основные внутренние API

- публичные ключи: `/api/encrypt_key/...`;
- передача новых приватных ключей: `/api/encrypt_key/new_private_key...`.

Точные публичные routes проксируются через Gateway/MessageService и считаются внутренним контрактом сервисов.

## База данных

PostgreSQL + JPA + стартовый SQL.

Основные таблицы:

- `public_keys` - активный публичный ключ пользователя.
- `send_new_key` - encrypted private-key handoff records.

## Зависимости

- PostgreSQL keyvault database

## Ручной запуск

```powershell
cd KeyVaultService
.\mvnw.cmd -q -DskipTests spring-boot:run
```

Обычно удобнее запускать через Docker Compose:

```powershell
docker compose up -d --build key-vault-service
```

## Принцип разработки

Сервис должен оставаться маленьким. Он хранит и отдает key metadata/envelopes, но не должен шифровать сообщения пользователей или хранить plaintext приватные ключи.