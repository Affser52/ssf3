import { API } from './constants.js';

export class ApiClient {
  async getCurrentUserId() {
    return this.get(API.me);
  }

  async getSettings() {
    return this.get(API.settings);
  }

  async updateProfileUsername(username) {
    return this.patchJson(API.settingsUsername, { username });
  }

  async updateAllowWriting(value) {
    return this.patchJson(API.settingsAllowWriting, { value: Boolean(value) });
  }

  async updateAllowAddChat(value) {
    return this.patchJson(API.settingsAllowAddChat, { value: Boolean(value) });
  }

  async changePassword(dto) {
    return this.postJson(API.settingsChangePassword, dto);
  }

  async logoutAllSessions() {
    return this.postJson(API.settingsLogoutAll, {});
  }

  async getChats() {
    return this.get(API.chats);
  }

  async getSidebarChats() {
    return this.get(API.sidebarChats);
  }

  async getMessages(chatId, limit) {
    return this.get(API.chatMessages(chatId, limit));
  }

  async searchUsers(username) {
    return this.get(API.searchUsers(username));
  }

  async getUserProfile(userId) {
    return this.get(API.userProfile(userId));
  }

  async getIdByUsername(username) {
    return this.get(API.getIdByUsername(username));
  }

  async getChatMembers(chatId) {
    return this.get(API.chatMembers(chatId));
  }

  async createPersonalChat(userId) {
    return this.postJson(API.createPersonalChat, { userId });
  }

  async createGroupChat(title, avatarUrl = '') {
    return this.postJson(API.createGroupChat, { title, avatarUrl });
  }

  async deleteChat(chatId) {
    return this.delete(API.deleteChat(chatId));
  }

  async addUserInChat(chatId, userTarget) {
    return this.postJson(API.addUserToChat, { chatId, userTarget });
  }

  async removeUserInChat(chatId, userTarget) {
    return this.postJson(API.removeUserFromChat, { chatId, userTarget });
  }

  async blockUserInChat(chatId, userId) {
    return this.postJson(API.blockUserInChat, { chatId, userId });
  }

  async sendMessage(dto) {
    return this.postJson(API.sendMessage, dto);
  }

  async createFileUploadUrl(chatId, file) {
    return this.postJson(API.fileUploadUrl, {
      chatId,
      ...this.#fileRequest(file)
    });
  }

  async completeFileUpload(fileId) {
    return this.postJson(API.fileComplete(fileId), {});
  }

  async createAvatarUploadUrl(file) {
    return this.postJson(API.settingsAvatarUploadUrl, this.#fileRequest(file));
  }

  async completeAvatarUpload(uploadId) {
    return this.postJson(API.settingsAvatarComplete(uploadId), {});
  }

  async getChatFiles(chatId) {
    return this.get(API.chatFiles(chatId));
  }

  async getFileDownloadUrl(fileId, options = {}) {
    return this.get(API.fileDownloadUrl(fileId, Boolean(options.inline)));
  }

  async deleteMessage(dto) {
    return this.postJson(API.deleteMessage, dto);
  }

  async getOwnPublicKey() {
    return this.get(API.ownPublicKey);
  }

  async saveOwnPublicKey(publicKeyBase64) {
    return this.postText(API.savePublicKey, publicKeyBase64);
  }

  async getPublicKeysByUsers(userIds) {
    return this.postJson(API.getPublicKeysByUsers, userIds);
  }

  async getPublicKeysByIds(ids) {
    return this.postJson(API.getPublicKeysByIds, ids);
  }

  async getNewPrivateKey() {
    return this.get(API.getNewPrivateKey);
  }

  async getNewPrivateKeyChain() {
    return this.get(API.getNewPrivateKeyChain);
  }

  async saveNewPrivateKey(dto) {
    return this.postJson(API.saveNewPrivateKey, dto);
  }

  async getPendingMessageKeys() {
    return this.get(API.getPendingMessageKeys);
  }

  async sendMessageKeys(dto) {
    return this.postJson(API.sendMessageKeys, dto);
  }

  async deletePendingMessageKey(keyId) {
    return this.postJson(API.deletePendingMessageKey, { keyId });
  }

  async getLatestKeyUsage(chatId) {
    return this.get(API.latestKeyUsage(chatId));
  }

  async logout() {
    await fetch('/auth/logout', { method: 'GET', credentials: 'include' });
  }

  async get(path) {
    return this.#request(path, { method: 'GET' });
  }

  async postJson(path, body) {
    return this.#request(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
  }

  async postText(path, text) {
    return this.#request(path, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: String(text ?? '')
    });
  }

  async patchJson(path, body) {
    return this.#request(path, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
  }

  async delete(path) {
    return this.#request(path, { method: 'DELETE' });
  }

  async #request(path, init) {
    const response = await fetch(path, {
      ...init,
      credentials: 'include'
    });

    if (response.status === 401) {
      window.location.href = '/auth/login';
      throw this.#error('Требуется авторизация.', 401);
    }

    if (response.status === 204) {
      return null;
    }

    const contentType = (response.headers.get('content-type') || '').toLowerCase();
    const text = await response.text();

    let payload = null;
    if (text) {
      if (contentType.includes('application/json')) {
        payload = this.#safeJson(text, text);
      } else {
        payload = this.#safeJson(text, text);
      }
    }

    if (!response.ok) {
      const message = this.#extractMessage(payload, response.status);
      throw this.#error(message, response.status, payload);
    }

    return payload;
  }

  #safeJson(value, fallback) {
    try {
      return JSON.parse(value);
    } catch {
      return fallback;
    }
  }

  #extractMessage(payload, status) {
    if (typeof payload === 'string' && payload.trim()) {
      return payload;
    }
    if (payload && typeof payload === 'object') {
      if (typeof payload.message === 'string' && payload.message.trim()) {
        return payload.message;
      }
      if (typeof payload.error === 'string' && payload.error.trim()) {
        return payload.error;
      }
    }
    return `Ошибка запроса (${status})`;
  }

  #error(message, status, payload = null) {
    const error = new Error(message || 'Ошибка запроса');
    error.status = status;
    error.payload = payload;
    return error;
  }

  #fileRequest(file) {
    return {
      originalFileName: file?.name || 'file',
      mimeType: file?.type || 'application/octet-stream',
      sizeBytes: Number(file?.size || 0)
    };
  }
}
