window.AppApi = (() => {
  const cfg = window.APP_CONFIG.api;

  async function request(url, options = {}) {
    const response = await fetch(url, {
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {})
      },
      ...options
    });

    const isJson = response.headers.get('content-type')?.includes('application/json');
    const body = isJson ? await response.json().catch(() => null) : await response.text().catch(() => null);

    if (!response.ok) {
      const message = typeof body === 'string'
        ? body
        : body?.message || body?.error || `HTTP ${response.status}`;
      const error = new Error(message || `HTTP ${response.status}`);
      error.status = response.status;
      error.body = body;
      throw error;
    }

    return body;
  }

  return {
    getCurrentUserId: () => request(cfg.getId, { method: 'GET', headers: { 'Content-Type': 'text/plain' } }),
    getChats: () => request(cfg.chats, { method: 'GET', headers: { 'Content-Type': 'text/plain' } }),
    savePublicKey: (payload) => request(cfg.savePublicKey, { method: 'POST', body: JSON.stringify(payload) }),
    getPublicKeys: (userIds) => request(cfg.getPublicKeys, { method: 'POST', body: JSON.stringify(userIds) }),
    sendEncryptMessageKey: (payload) => request(cfg.sendEncryptMessageKey, { method: 'POST', body: JSON.stringify(payload) }),
    deleteEncryptMessageKey: (payload) => request(cfg.deleteEncryptMessageKey, { method: 'POST', body: JSON.stringify(payload) }),
    getLastMessages: (count) => request(cfg.getLastMessages(count), { method: 'GET', headers: { 'Content-Type': 'text/plain' } }),
    getMessagesRelative: (messageId, count) => request(cfg.getMessagesRelative(messageId, count), { method: 'GET', headers: { 'Content-Type': 'text/plain' } }),
    sendMessage: (payload) => request(cfg.sendMessage, { method: 'POST', body: JSON.stringify(payload) }),
    sendMessageAndCreateChat: (payload) => request(cfg.sendMessageAndNewChat, { method: 'POST', body: JSON.stringify(payload) }),
    searchByUsername: (username) => request(cfg.searchByUsername(username), { method: 'GET', headers: { 'Content-Type': 'text/plain' } })
  };
})();
