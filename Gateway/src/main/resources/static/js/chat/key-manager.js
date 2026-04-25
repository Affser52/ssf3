import { LIMITS } from './constants.js';
import { normalizeBase64 } from './base64.js';

export class KeyManager {
  constructor({ api, db, crypto, ui }) {
    this.api = api;
    this.db = db;
    this.crypto = crypto;
    this.ui = ui;
    this.userId = null;
    this.currentKey = null;
    this.retrySenderKeysInFlight = false;
  }

  async init(userId) {
    this.userId = userId;
    this.currentKey = await this.db.getCurrentUserKey(userId);
  }

  async bootstrap() {
    this.currentKey = await this.db.getCurrentUserKey(this.userId);
    const serverKey = await this.#safeGetOwnPublicKey();

    if (!this.currentKey && !serverKey) {
      await this.#createAndUploadNewCurrentKey();
      this.ui.setKeyState('Создана новая пара user-ключей.', true);
      return;
    }

    if (this.currentKey && !serverKey) {
      await this.api.saveOwnPublicKey(this.currentKey.publicKeyB64);
      const fresh = await this.#safeGetOwnPublicKey();
      if (fresh) {
        await this.#setCurrent({
          publicKeyId: fresh.id,
          publicKeyB64: fresh.keyB64,
          privateKeyB64: this.currentKey.privateKeyB64
        });
      }
      this.ui.setKeyState('Публичный ключ восстановлен на сервере.', true);
      return;
    }

    if (!this.currentKey && serverKey) {
      const localRestored = await this.#tryUseLocalUserKey(serverKey);
      if (localRestored) {
        this.ui.setKeyState('Найден локальный user-ключ для текущего аккаунта.', true);
        return;
      }

      const restored = await this.#tryRestoreFromPending(serverKey.id);
      if (!restored) {
        await this.#createAndUploadNewCurrentKey(serverKey);
        this.ui.setKeyState('Создана новая пара user-ключей для текущего аккаунта.', true);
        return;
      }
      this.ui.setKeyState('Ключи пользователя готовы.', true);
      return;
    }

    if (this.#isSamePublicKey(this.currentKey, serverKey)) {
      await this.#setCurrent({
        publicKeyId: serverKey.id,
        publicKeyB64: serverKey.keyB64,
        privateKeyB64: this.currentKey.privateKeyB64
      });
      this.ui.setKeyState('Ключи пользователя синхронизированы.', true);
      return;
    }

    const localRestored = await this.#tryUseLocalUserKey(serverKey);
    if (localRestored) {
      this.ui.setKeyState('Найден локальный user-ключ для текущего аккаунта.', true);
      return;
    }

    const restored = await this.#tryRestoreFromPending(serverKey.id);
    if (!restored) {
      await this.#createAndUploadNewCurrentKey(serverKey);
      this.ui.setKeyState('Создана новая пара user-ключей для текущего аккаунта.', true);
      return;
    }
    this.ui.setKeyState('Ключи пользователя обновлены.', true);
  }

  async syncUserPrivateKeysFromServer() {
    const serverKey = await this.#safeGetOwnPublicKey();
    if (!serverKey?.id && !serverKey?.keyB64) {
      return { imported: false };
    }

    this.currentKey = await this.db.getCurrentUserKey(this.userId);
    if (this.#isSamePublicKey(this.currentKey, serverKey)) {
      return { imported: false };
    }

    const localRestored = await this.#tryUseLocalUserKey(serverKey);
    if (localRestored) {
      this.ui.setKeyState('Найден локальный user-ключ для текущего аккаунта.', true);
      return { imported: true };
    }

    const restored = await this.#tryRestoreFromPending(serverKey.id);
    if (restored) {
      this.ui.setKeyState('Получен новый user-ключ для текущего аккаунта.', true);
      return { imported: true };
    }

    return { imported: false };
  }

  async ensureSenderKey(chatId, memberIds) {
    const currentUsage = await this.db.getUsage(this.userId, chatId);

    if (currentUsage?.encryptName && Number(currentUsage.count || 0) < LIMITS.maxMessagesPerSenderKey) {
      const localSenderKey = await this.db.getSenderKey(this.userId, currentUsage.encryptName);
      if (localSenderKey?.keyB64 && Number(localSenderKey.chatId) === Number(chatId)) {
        return { encryptName: currentUsage.encryptName, keyB64: localSenderKey.keyB64 };
      }
    }

    await this.ingestPendingMessageKeys();

    const afterSyncUsage = await this.db.getUsage(this.userId, chatId);
    if (afterSyncUsage?.encryptName && Number(afterSyncUsage.count || 0) < LIMITS.maxMessagesPerSenderKey) {
      const localSenderKey = await this.db.getSenderKey(this.userId, afterSyncUsage.encryptName);
      if (localSenderKey?.keyB64 && Number(localSenderKey.chatId) === Number(chatId)) {
        return { encryptName: afterSyncUsage.encryptName, keyB64: localSenderKey.keyB64 };
      }
    }

    return this.rotateSenderKey(chatId, memberIds);
  }
  async shareAllSenderKeysForChatWithUsers(chatId, targetUserIds = []) {
    const targets = Array.from(new Set((targetUserIds || [])
      .map((value) => String(value || '').trim())
      .filter((value) => value && value !== String(this.userId))));

    if (!chatId || targets.length === 0) {
      return { sent: 0, failed: 0, skipped: 0 };
    }

    const senderKeys = (await this.db.listSenderKeys(this.userId))
      .filter((key) => Number(key.chatId) === Number(chatId) && key.encryptName && key.keyB64);

    if (senderKeys.length === 0) {
      return { sent: 0, failed: 0, skipped: targets.length };
    }

    let sent = 0;
    let failed = 0;
    for (const senderKey of senderKeys) {
      const result = await this.shareSenderKeyByEncryptNameWithUsers(chatId, senderKey.encryptName, targets);
      sent += result.sent;
      failed += result.failed;
    }

    if (sent > 0) {
      this.ui.appendStatus('Sender keys sent to the new member: ' + sent + '.', 'ok');
    }

    return { sent, failed, skipped: 0 };
  }
  async shareSenderKeyByEncryptNameWithUsers(chatId, encryptName, targetUserIds = []) {
    const cleanEncryptName = String(encryptName || '').trim();
    const targets = Array.from(new Set((targetUserIds || [])
      .map((value) => String(value || '').trim())
      .filter((value) => value && value !== String(this.userId))));

    if (!chatId || !cleanEncryptName || targets.length === 0) {
      return { sent: 0, failed: 0, skipped: 0 };
    }

    const senderKey = await this.db.getSenderKey(this.userId, cleanEncryptName);
    if (!senderKey?.keyB64 || Number(senderKey.chatId) !== Number(chatId)) {
      return { sent: 0, failed: 0, skipped: targets.length };
    }

    let publicKeys = [];
    try {
      publicKeys = await this.api.getPublicKeysByUsers(targets);
    } catch {
      await this.#rememberFailedSenderKeyDeliveries(chatId, cleanEncryptName, targets);
      return { sent: 0, failed: targets.length, skipped: 0 };
    }

    const selectedKeys = targets.map((targetUserId) => (
      ((publicKeys || []).find((key) => String(key.userId) === String(targetUserId)) || {
        userId: targetUserId,
        id: null,
        key: null
      })
    ));

    const { requestRows, failedDeliveries } = await this.#buildMessageKeyRows({
      chatId,
      senderKeyB64: senderKey.keyB64,
      publicKeys: selectedKeys
    });

    if (requestRows.length === 0) {
      await this.#rememberFailedSenderKeyDeliveries(
        chatId,
        cleanEncryptName,
        failedDeliveries.map((item) => item.userTarget).filter(Boolean)
      );
      return { sent: 0, failed: failedDeliveries.length, skipped: targets.length };
    }

    try {
      await this.api.sendMessageKeys({
        chatId,
        encryptName: cleanEncryptName,
        requestEncryptMessageKeyForUsers: requestRows
      });
      return {
        sent: requestRows.length,
        failed: failedDeliveries.length,
        skipped: Math.max(0, targets.length - selectedKeys.length)
      };
    } catch {
      await this.#rememberFailedSenderKeyDeliveries(chatId, cleanEncryptName, targets);
      return {
        sent: 0,
        failed: failedDeliveries.length + requestRows.length,
        skipped: Math.max(0, targets.length - selectedKeys.length)
      };
    }
  }

  async rotateSenderKey(chatId, memberIds) {
    const publicKeys = await this.api.getPublicKeysByUsers(memberIds);
    if (!Array.isArray(publicKeys) || publicKeys.length === 0) {
      throw new Error('Не удалось получить публичные ключи участников.');
    }

    const senderKeyB64 = this.crypto.generateSenderKey();
    const { requestRows, failedDeliveries } = await this.#buildMessageKeyRows({
      chatId,
      senderKeyB64,
      publicKeys
    });

    if (requestRows.length === 0) {
      throw new Error('Нет валидных публичных ключей для отправки sender key.');
    }

    const result = await this.api.sendMessageKeys({
      chatId,
      requestEncryptMessageKeyForUsers: requestRows
    });

    const encryptName = String(result?.encryptName || '');
    if (!encryptName) {
      throw new Error('Сервер не вернул encryptName.');
    }

    await this.db.upsertSenderKey(this.userId, {
      chatId,
      encryptName,
      keyB64: senderKeyB64,
      publicKeyId: null
    });
    await this.db.setUsage(this.userId, chatId, encryptName, 0);

    await this.#rememberFailedSenderKeyDeliveries(
      chatId,
      encryptName,
      failedDeliveries.map((item) => item.userTarget).filter(Boolean)
    );

    return { encryptName, keyB64: senderKeyB64 };
  }

  async retryFailedSenderKeyDeliveries() {
    if (this.retrySenderKeysInFlight) {
      return { retried: 0, sent: 0, failed: 0 };
    }

    const pending = this.#readFailedSenderKeyDeliveries();
    if (pending.length === 0) {
      return { retried: 0, sent: 0, failed: 0 };
    }

    this.retrySenderKeysInFlight = true;
    let retried = 0;
    let sent = 0;
    let failed = 0;
    const stillPending = [];

    try {
      for (const item of pending) {
        const chatId = item?.chatId;
        const encryptName = item?.encryptName;
        const targets = Array.from(new Set((item?.targets || [])
          .map((value) => String(value || '').trim())
          .filter(Boolean)));

        if (!chatId || !encryptName || targets.length === 0) {
          continue;
        }

        retried += targets.length;
        const result = await this.shareSenderKeyByEncryptNameWithUsers(chatId, encryptName, targets);
        sent += result.sent || 0;
        failed += result.failed || 0;

        if ((result.failed || 0) > 0) {
          stillPending.push(item);
        }
      }

      this.#writeFailedSenderKeyDeliveries(stillPending);
      return { retried, sent, failed };
    } finally {
      this.retrySenderKeysInFlight = false;
    }
  }
  async incrementUsage(chatId, encryptName) {
    return this.db.incrementUsage(this.userId, chatId, encryptName);
  }

  async getSenderKey(encryptName) {
    return this.db.getSenderKey(this.userId, encryptName);
  }

  async hasSenderKey(encryptName) {
    if (!encryptName) {
      return false;
    }
    const senderKey = await this.db.getSenderKey(this.userId, encryptName);
    return Boolean(senderKey?.keyB64);
  }

  async getDiagnostics() {
    const currentKey = await this.db.getCurrentUserKey(this.userId);
    const userKeys = await this.db.listUserKeys(this.userId);
    const senderKeys = await this.db.listSenderKeys(this.userId);
    const serverKey = await this.#safeGetOwnPublicKey();
    const pendingPrivateKey = await this.#safeReadPendingPrivateKey();

    return {
      userId: this.userId,
      currentPublicKeyId: currentKey?.publicKeyId || null,
      serverPublicKeyId: serverKey?.id || null,
      localUserKeyCount: userKeys.length,
      localSenderKeyCount: senderKeys.length,
      synchronized: this.#isSamePublicKey(currentKey, serverKey),
      hasPendingPrivateKey: Boolean(pendingPrivateKey?.id),
      pendingPrivateKeyPublicId: pendingPrivateKey?.publicKey || null
    };
  }

  async rotateUserKeyPair() {
    await this.bootstrap();

    const currentKey = await this.db.getCurrentUserKey(this.userId);
    if (!currentKey?.publicKeyB64 || !currentKey?.publicKeyId) {
      throw new Error('Нет текущего ключа пользователя для ротации.');
    }

    const generated = await this.crypto.generateUserKeyPair();
    await this.api.saveOwnPublicKey(generated.publicKeyB64);
    const fresh = await this.#safeGetOwnPublicKey();

    if (!fresh?.id || !fresh?.keyB64) {
      throw new Error('Сервер не вернул новый публичный ключ после ротации.');
    }

    const encryptedPrivateKey = await this.crypto.encryptForPublicKey(
      generated.privateKeyB64,
      currentKey.publicKeyB64
    );

    await this.api.saveNewPrivateKey({
      userId: this.userId,
      key: encryptedPrivateKey,
      publicKey: fresh.id,
      encryptingPublicKey: currentKey.publicKeyId
    });

    await this.#setCurrent({
      publicKeyId: fresh.id,
      publicKeyB64: fresh.keyB64,
      privateKeyB64: generated.privateKeyB64
    });

    this.ui.setKeyState('Пользовательский ключ обновлен.', true);
    return fresh;
  }

  async createSessionUserKeyPair() {
    const generated = await this.crypto.generateUserKeyPair();
    await this.api.saveOwnPublicKey(generated.publicKeyB64);
    const fresh = await this.#safeGetOwnPublicKey();

    if (!fresh?.id || !fresh?.keyB64) {
      throw new Error('Сервер не вернул новый публичный ключ для текущей сессии.');
    }

    await this.#setCurrent({
      publicKeyId: fresh.id,
      publicKeyB64: fresh.keyB64,
      privateKeyB64: generated.privateKeyB64
    });

    this.ui.setKeyState('Создан новый пользовательский ключ для текущей сессии.', true);
    return fresh;
  }

  async ingestPendingMessageKeys() {
    const pending = await this.#safeGetPendingMessageKeys();
    if (pending.length === 0) {
      return { importedCount: 0, encryptNames: [] };
    }

    const importedEncryptNames = [];
    let deletedCount = 0;
    let deleteFailedCount = 0;

    for (const item of pending) {
      const encryptName = item.encryptName;
      if (!encryptName) continue;

      const keyExists = await this.db.getSenderKey(this.userId, encryptName);
      if (keyExists) {
        if (item.id) {
          const deleted = await this.#safeDeletePending(item.id);
          deletedCount += deleted ? 1 : 0;
          deleteFailedCount += deleted ? 0 : 1;
        }
        continue;
      }

      const publicKeyId = item.publicKey || item.publicKeyUser;
      if (!publicKeyId) continue;
      const privateKeyEntity = await this.db.findPrivateKeyByPublicId(this.userId, publicKeyId);
      if (!privateKeyEntity?.privateKeyB64) continue;

      const encryptedKeyB64 = normalizeBase64(item.key);
      if (!encryptedKeyB64) continue;

      let senderKeyB64;
      try {
        senderKeyB64 = await this.crypto.decryptWithPrivateKey(encryptedKeyB64, privateKeyEntity.privateKeyB64);
      } catch {
        continue;
      }

      await this.db.upsertSenderKey(this.userId, {
        chatId: item.chatId ?? null,
        encryptName,
        keyB64: senderKeyB64,
        publicKeyId
      });
      importedEncryptNames.push(encryptName);

      if (item.id) {
        const deleted = await this.#safeDeletePending(item.id);
        deletedCount += deleted ? 1 : 0;
        deleteFailedCount += deleted ? 0 : 1;
      }
    }

    if (deleteFailedCount > 0) {
      this.ui.appendStatus('Не удалось удалить часть полученных ключей с сервера. Повторим при следующей синхронизации.', 'info');
    }

    return {
      importedCount: importedEncryptNames.length,
      encryptNames: importedEncryptNames,
      deletedCount,
      deleteFailedCount
    };
  }

  async decryptMessage(messageB64, encryptName) {
    if (!messageB64) return '';
    if (!encryptName) {
      return this.#bestEffortDecode(messageB64);
    }

    const senderKey = await this.db.getSenderKey(this.userId, encryptName);
    if (!senderKey?.keyB64) {
      return null;
    }

    try {
      return await this.crypto.decryptMessage(messageB64, senderKey.keyB64);
    } catch {
      return null;
    }
  }

  async #buildMessageKeyRows({ chatId, senderKeyB64, publicKeys, pendingItems = [] }) {
    const requestRows = [];
    const failedDeliveries = [];

    for (const key of publicKeys) {
      const publicKeyB64 = normalizeBase64(key.key);
      const pendingItem = pendingItems.find((item) => String(item.userTarget) === String(key.userId)) || null;
      const userTarget = key.userId || pendingItem?.userTarget;
      const publicKeyUser = key.id || pendingItem?.publicKeyUser;

      if (!publicKeyB64) {
        failedDeliveries.push({ userTarget, publicKeyUser, reason: 'public key is empty' });
        continue;
      }

      let encryptedSenderKey;
      try {
        encryptedSenderKey = await this.crypto.encryptForPublicKey(senderKeyB64, publicKeyB64);
      } catch (error) {
        failedDeliveries.push({ userTarget, publicKeyUser, reason: error?.message || 'public key import failed' });
        console.warn('Пропущен некорректный публичный ключ участника.', {
          userId: userTarget,
          keyId: publicKeyUser,
          error: error?.message || error
        });
        continue;
      }

      requestRows.push({
        userTarget,
        key: encryptedSenderKey,
        publicKeyUser
      });
    }

    return { requestRows, failedDeliveries };
  }

  async #rememberFailedSenderKeyDeliveries(chatId, encryptName, targetUserIds = []) {
    const targets = Array.from(new Set((targetUserIds || [])
      .map((value) => String(value || '').trim())
      .filter((value) => value && value !== String(this.userId))));

    if (!chatId || !encryptName || targets.length === 0) {
      return;
    }

    const existing = this.#readFailedSenderKeyDeliveries();
    const key = `${chatId}:${encryptName}`;
    const byKey = new Map(existing.map((item) => [`${item.chatId}:${item.encryptName}`, item]));
    const current = byKey.get(key) || { chatId: Number(chatId), encryptName: String(encryptName), targets: [] };
    current.targets = Array.from(new Set([...(current.targets || []), ...targets]));
    byKey.set(key, current);
    this.#writeFailedSenderKeyDeliveries(Array.from(byKey.values()));
  }

  #readFailedSenderKeyDeliveries() {
    try {
      const raw = localStorage.getItem(this.#failedSenderKeysStorageKey());
      const payload = raw ? JSON.parse(raw) : [];
      return Array.isArray(payload) ? payload : [];
    } catch {
      return [];
    }
  }

  #writeFailedSenderKeyDeliveries(items) {
    const clean = (items || [])
      .map((item) => ({
        chatId: Number(item.chatId),
        encryptName: String(item.encryptName || ''),
        targets: Array.from(new Set((item.targets || [])
          .map((value) => String(value || '').trim())
          .filter(Boolean)))
      }))
      .filter((item) => item.chatId && item.encryptName && item.targets.length > 0);

    localStorage.setItem(this.#failedSenderKeysStorageKey(), JSON.stringify(clean));
  }

  #failedSenderKeysStorageKey() {
    return `mescat_failed_sender_keys:${this.userId}`;
  }

  async #createAndUploadNewCurrentKey(previousServerKey = null) {
    const generated = await this.crypto.generateUserKeyPair();
    await this.api.saveOwnPublicKey(generated.publicKeyB64);
    const serverKey = await this.#safeGetOwnPublicKey();
    const publicKeyId = serverKey?.id || crypto.randomUUID();
    const publicKeyB64 = serverKey?.keyB64 || generated.publicKeyB64;

    if (this.#canPublishNewPrivateKey(previousServerKey, publicKeyId)) {
      const encryptedPrivateKey = await this.crypto.encryptForPublicKey(
        generated.privateKeyB64,
        previousServerKey.keyB64
      );

      await this.api.saveNewPrivateKey({
        userId: this.userId,
        key: encryptedPrivateKey,
        publicKey: publicKeyId,
        encryptingPublicKey: previousServerKey.id
      });
    }

    await this.#setCurrent({
      publicKeyId,
      publicKeyB64,
      privateKeyB64: generated.privateKeyB64
    });
  }

  #canPublishNewPrivateKey(previousServerKey, nextPublicKeyId) {
    return Boolean(
      previousServerKey?.id
      && previousServerKey?.keyB64
      && nextPublicKeyId
      && String(previousServerKey.id) !== String(nextPublicKeyId)
    );
  }

  async #setCurrent(key) {
    await this.db.setCurrentUserKey(this.userId, {
      publicKeyId: key.publicKeyId,
      publicKeyB64: key.publicKeyB64,
      privateKeyB64: key.privateKeyB64
    });
    this.currentKey = await this.db.getCurrentUserKey(this.userId);
  }

  async #tryUseLocalUserKey(serverKey) {
    if (!serverKey?.id && !serverKey?.keyB64) {
      return false;
    }

    const serverPublicKeyId = serverKey.id ? String(serverKey.id) : null;
    const serverPublicKeyB64 = normalizeBase64(serverKey.keyB64);
    const localKeys = await this.db.listUserKeys(this.userId);

    const matchedKey = localKeys.find((key) => {
      const sameId = serverPublicKeyId && key.publicKeyId && String(key.publicKeyId) === serverPublicKeyId;
      const samePublicKey = serverPublicKeyB64 && normalizeBase64(key.publicKeyB64) === serverPublicKeyB64;
      return key.privateKeyB64 && (sameId || samePublicKey);
    });

    if (!matchedKey) {
      return false;
    }

    await this.#setCurrent({
      publicKeyId: serverPublicKeyId || matchedKey.publicKeyId,
      publicKeyB64: serverPublicKeyB64 || matchedKey.publicKeyB64,
      privateKeyB64: matchedKey.privateKeyB64
    });
    return true;
  }

  async #safeGetOwnPublicKey() {
    try {
      const payload = await this.api.getOwnPublicKey();
      if (!payload || typeof payload !== 'object') {
        return null;
      }
      return {
        id: payload.id ? String(payload.id) : null,
        userId: payload.userId ? String(payload.userId) : null,
        keyB64: normalizeBase64(payload.key)
      };
    } catch (error) {
      if (error.status === 404 || error.status === 204) {
        return null;
      }
      throw error;
    }
  }

  async #safeGetPendingMessageKeys() {
    try {
      const payload = await this.api.getPendingMessageKeys();
      return Array.isArray(payload) ? payload : [];
    } catch (error) {
      if (error.status === 404 || error.status === 204) {
        return [];
      }
      throw error;
    }
  }

  async #safeDeletePending(id) {
    try {
      await this.api.deletePendingMessageKey(id);
      return true;
    } catch {
      return false;
    }
  }

  async #safeReadPendingPrivateKey() {
    try {
      const payload = await this.api.getNewPrivateKey();
      return payload && typeof payload === 'object' ? payload : null;
    } catch (error) {
      if (error.status === 404 || error.status === 204) {
        return null;
      }
      throw error;
    }
  }

  async #safeReadPendingPrivateKeyChain() {
    try {
      const payload = typeof this.api.getNewPrivateKeyChain === 'function'
        ? await this.api.getNewPrivateKeyChain()
        : await this.api.getNewPrivateKey();
      if (Array.isArray(payload)) {
        return payload;
      }
      return payload && typeof payload === 'object' ? [payload] : [];
    } catch (error) {
      if (error.status === 404 || error.status === 204) {
        return [];
      }
      throw error;
    }
  }

  async #tryRestoreFromPending(expectedPublicKeyId) {
    const localKeys = await this.db.listUserKeys(this.userId);
    const targetPublicKeyId = expectedPublicKeyId ? String(expectedPublicKeyId) : null;
    const targetLocalKey = targetPublicKeyId
      ? localKeys.find((key) => String(key.publicKeyId) === targetPublicKeyId && key.privateKeyB64 && key.publicKeyB64)
      : null;
    if (targetLocalKey) {
      await this.#setCurrent({
        publicKeyId: targetLocalKey.publicKeyId,
        publicKeyB64: targetLocalKey.publicKeyB64,
        privateKeyB64: targetLocalKey.privateKeyB64
      });
      return true;
    }

    const pendingItems = await this.#safeReadPendingPrivateKeyChain();
    if (pendingItems.length === 0) {
      return false;
    }

    const ownItems = pendingItems
      .filter((item) => item && typeof item === 'object')
      .filter((item) => !item.userId || String(item.userId) === String(this.userId))
      .sort((a, b) => String(a.createdAt || '').localeCompare(String(b.createdAt || '')));

    if (ownItems.length === 0) {
      return false;
    }

    const knownPrivateKeys = new Map(
      localKeys
        .filter((key) => key.publicKeyId && key.privateKeyB64)
        .map((key) => [String(key.publicKeyId), key.privateKeyB64])
    );

    if (knownPrivateKeys.size === 0) {
      return false;
    }

    const publicIds = Array.from(new Set(
      ownItems
        .map((item) => item.publicKey ? String(item.publicKey) : null)
        .filter(Boolean)
    ));
    const publicKeys = publicIds.length > 0 ? await this.api.getPublicKeysByIds(publicIds) : [];
    const publicKeyById = new Map(
      (Array.isArray(publicKeys) ? publicKeys : [])
        .filter((key) => key?.id && key?.key)
        .map((key) => [String(key.id), normalizeBase64(key.key)])
    );

    const restoredPublicKeys = new Set();
    let restoredCurrent = false;
    let progress = true;

    while (progress) {
      progress = false;

      for (const pending of ownItems) {
        const pendingPublic = pending.publicKey ? String(pending.publicKey) : null;
        const encryptingPublicKey = pending.encryptingPublicKey ? String(pending.encryptingPublicKey) : null;

        if (!pendingPublic || !encryptingPublicKey || restoredPublicKeys.has(pendingPublic)) {
          continue;
        }

        const decryptorPrivateKey = knownPrivateKeys.get(encryptingPublicKey);
        if (!decryptorPrivateKey) {
          continue;
        }

        const encryptedPrivateB64 = normalizeBase64(pending.key);
        const resolvedPublic = publicKeyById.get(pendingPublic);
        if (!encryptedPrivateB64 || !resolvedPublic) {
          continue;
        }

        let decryptedPrivateB64;
        try {
          decryptedPrivateB64 = await this.crypto.decryptWithPrivateKey(encryptedPrivateB64, decryptorPrivateKey);
        } catch {
          continue;
        }

        knownPrivateKeys.set(pendingPublic, decryptedPrivateB64);
        restoredPublicKeys.add(pendingPublic);
        await this.#setCurrent({
          publicKeyId: pendingPublic,
          publicKeyB64: resolvedPublic,
          privateKeyB64: decryptedPrivateB64
        });

        progress = true;
        if (!targetPublicKeyId || pendingPublic === targetPublicKeyId) {
          restoredCurrent = true;
        }
      }
    }

    return restoredCurrent || (targetPublicKeyId && knownPrivateKeys.has(targetPublicKeyId));
  }

  #isSamePublicKey(localKey, serverKey) {
    if (!localKey || !serverKey) return false;
    if (localKey.publicKeyId && serverKey.id && String(localKey.publicKeyId) === String(serverKey.id)) {
      return true;
    }
    return localKey.publicKeyB64 && serverKey.keyB64 && localKey.publicKeyB64 === serverKey.keyB64;
  }

  #bestEffortDecode(messageB64) {
    try {
      return decodeURIComponent(escape(atob(messageB64)));
    } catch {
      return '[Сообщение зашифровано]';
    }
  }
}

