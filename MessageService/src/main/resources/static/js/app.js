(() => {
  const state = {
    userId: null,
    chats: [],
    activeChat: null,
    chatMessages: new Map(),
    oldestMessageByChat: new Map()
  };

  const els = {
    searchInput: document.getElementById('userSearchInput'),
    sendBtn: document.getElementById('sendMessageBtn'),
    messageInput: document.getElementById('messageInput'),
    loadMoreBtn: document.getElementById('loadMoreBtn'),
    reloadChatsBtn: document.getElementById('reloadChatsBtn'),
    rotateKeyBtn: document.getElementById('rotateKeyBtn')
  };

  document.addEventListener('DOMContentLoaded', init);

  async function init() {
    bindEvents();
    AppUI.setConnectionState(false);

    try {
      await bootstrapUser();
      connectWsWriteOnly();
      await loadChats();
      await loadLastMessagesSnapshot();

      const firstChat = state.chats[0];
      if (firstChat) {
        await openChat(firstChat.chatId);
      }
    } catch (error) {
      AppUI.showToast(error.message || 'Ошибка инициализации');
    }
  }

  function bindEvents() {
    AppUI.onChatClick((chat) => openChat(chat.chatId));
    AppUI.onSearchClick((item) => selectSearchItem(item));

    els.reloadChatsBtn.addEventListener('click', async () => {
      try { await loadChats(); } catch (e) { AppUI.showToast(e.message); }
    });

    els.sendBtn.addEventListener('click', sendCurrentMessage);
    els.rotateKeyBtn.addEventListener('click', async () => {
      try {
        ensureActiveChat();
        await ensureChatKey(state.activeChat, true);
        AppUI.showToast('Ключ чата обновлен', 'success');
      } catch (e) {
        AppUI.showToast(e.message);
      }
    });

    els.loadMoreBtn.addEventListener('click', loadOlderMessages);

    let searchTimer = null;
    els.searchInput.addEventListener('input', () => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(runSearch, 300);
    });

    els.messageInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendCurrentMessage();
      }
    });
  }

  async function bootstrapUser() {
    let userId = AppStorage.getUserId();
    if (!userId) {
      userId = await AppApi.getCurrentUserId();
      if (typeof userId !== 'string') {
        throw new Error('Сервер не вернул userId в ожидаемом формате');
      }
      AppStorage.setUserId(userId);
    }
    state.userId = userId;

    let userKeys = AppStorage.getUserKeys();
    if (!userKeys?.publicKey || !userKeys?.privateKey) {
      userKeys = await AppCrypto.generateUserKeyPair();
      await AppApi.savePublicKey({
        id: state.userId,
        publicKey: userKeys.publicKey
      });
      AppStorage.setUserKeys(userKeys);
    }
  }

  function connectWsWriteOnly() {
    AppWs.connect({
      onOpen: () => AppUI.setConnectionState(true),
      onClose: () => AppUI.setConnectionState(false),
      onError: () => AppUI.setConnectionState(false),
      onMessage: () => {}
    });
  }

  async function loadChats() {
    const chats = await AppApi.getChats();
    state.chats = Array.isArray(chats) ? chats : [];
    AppUI.renderChats(state.chats, state.activeChat?.chatId);
  }

  async function loadLastMessagesSnapshot() {
    const response = await AppApi.getLastMessages(window.APP_CONFIG.historyPageSize);
    const list = Array.isArray(response) ? response : [];

    const grouped = new Map();
    for (const item of list) {
      const chatId = item.chatId || item.chat?.chatId;
      if (!chatId) continue;
      if (!grouped.has(String(chatId))) grouped.set(String(chatId), []);
      grouped.get(String(chatId)).push(item);
    }

    for (const [chatId, messages] of grouped.entries()) {
      const normalized = await normalizeMessages(messages, chatId);
      state.chatMessages.set(chatId, normalized);
      if (normalized.length > 0) {
        state.oldestMessageByChat.set(chatId, normalized[0]);
      }
    }
  }

  async function openChat(chatId) {
    const chat = state.chats.find((c) => String(c.chatId) === String(chatId));
    if (!chat) return;
    state.activeChat = chat;
    AppUI.setActiveChat(chat);
    AppUI.renderChats(state.chats, chat.chatId);

    const key = String(chat.chatId);
    if (!state.chatMessages.has(key)) {
      state.chatMessages.set(key, []);
    }

    const messages = state.chatMessages.get(key) || [];
    AppUI.renderMessages(messages, state.userId);
    AppUI.scrollToBottom();
    AppUI.toggleLoadMore(messages.length > 0);
  }

  async function loadOlderMessages() {
    try {
      ensureActiveChat();
      const chatKey = String(state.activeChat.chatId);
      const oldest = state.oldestMessageByChat.get(chatKey);
      if (!oldest?.messageId) {
        AppUI.toggleLoadMore(false);
        return;
      }

      const response = await AppApi.getMessagesRelative(oldest.messageId, window.APP_CONFIG.historyPageSize);
      const normalized = await normalizeMessages(Array.isArray(response) ? response : [], chatKey);
      const existing = state.chatMessages.get(chatKey) || [];
      const merged = mergeMessages(normalized, existing);
      state.chatMessages.set(chatKey, merged);
      if (merged.length > 0) state.oldestMessageByChat.set(chatKey, merged[0]);
      AppUI.prependMessages(normalized, state.userId);
      if (!normalized.length) AppUI.toggleLoadMore(false);
    } catch (error) {
      AppUI.showToast(error.message || 'Не удалось догрузить сообщения');
    }
  }

  async function runSearch() {
    const q = els.searchInput.value.trim();
    if (q.length < 2) {
      AppUI.clearSearchResults();
      return;
    }
    try {
      const result = await AppApi.searchByUsername(q);
      AppUI.renderSearchResults(Array.isArray(result) ? result : []);
    } catch (error) {
      AppUI.showToast(error.message || 'Ошибка поиска');
    }
  }

  async function selectSearchItem(item) {
    AppUI.clearSearchResults();
    els.searchInput.value = '';

    const existing = state.chats.find((chat) =>
      item.chatId && String(chat.chatId) === String(item.chatId)
    );
    if (existing) {
      await openChat(existing.chatId);
      return;
    }

    state.activeChat = {
      chatId: null,
      title: item.username || item.title || 'Новый чат',
      username: item.username,
      targetUserId: item.userId || item.id || item.targetUserId || null,
      isNewDialog: true
    };
    AppUI.setActiveChat(state.activeChat);
    AppUI.renderMessages([], state.userId);
    AppUI.toggleLoadMore(false);
  }

  async function sendCurrentMessage() {
    try {
      ensureActiveChat();
      const text = els.messageInput.value.trim();
      if (!text) return;

      const chatKey = await ensureChatKey(state.activeChat);
      const encryptedMessage = await AppCrypto.encryptTextWithChatKey(text, chatKey.keyBase64);

      let response;
      if (state.activeChat.isNewDialog) {
        if (!state.activeChat.targetUserId) {
          throw new Error('Не найден target userId для нового чата');
        }
        response = await AppApi.sendMessageAndCreateChat({
          targetId: state.activeChat.targetUserId,
          message: encryptedMessage,
          encryptionName: chatKey.encryptName
        });
        await loadChats();
      } else {
        const payload = {
          chatId: state.activeChat.chatId,
          message: encryptedMessage,
          encryptionName: chatKey.encryptName
        };
        response = await trySendMessage(payload);
      }

      const uiMessage = await normalizeOutgoingMessage(response, text);
      const chatId = String(uiMessage.chatId || state.activeChat.chatId || 'temp');
      const list = state.chatMessages.get(chatId) || [];
      list.push(uiMessage);
      state.chatMessages.set(chatId, list);
      AppUI.appendMessage(uiMessage, state.userId);
      els.messageInput.value = '';

      const currentCount = AppStorage.incCounter(chatId);
      if (currentCount % window.APP_CONFIG.keyRotationMessageLimit === 0) {
        await ensureChatKey(state.activeChat, true);
      }
    } catch (error) {
      AppUI.showToast(error.message || 'Ошибка отправки сообщения');
    }
  }

  async function trySendMessage(payload) {
    if (AppWs.isConnected()) {
      try {
        AppWs.send(payload);
        return {
          ...payload,
          messageId: crypto.randomUUID(),
          senderId: state.userId,
          createdAt: new Date().toISOString(),
          clientOnly: true
        };
      } catch {}
    }
    return AppApi.sendMessage(payload);
  }

  async function ensureChatKey(chat, forceRotate = false) {
    const chatStorageKey = String(chat.chatId || chat.targetUserId || chat.username);
    const existing = AppStorage.getChatKey(chatStorageKey);
    if (existing && !forceRotate) return existing;

    const chatKey = await AppCrypto.generateChatKey();
    const targetUserIds = await resolveChatMemberIds(chat);
    if (!targetUserIds.length) throw new Error('Не удалось определить участников чата для рассылки ключа');

    const publicKeysResponse = await AppApi.getPublicKeys(targetUserIds);
    const publicKeys = Array.isArray(publicKeysResponse) ? publicKeysResponse : [];

    const entries = [];
    for (const item of publicKeys) {
      const publicKey = item.publicKey || item.key || item.value;
      const targetUserId = item.id || item.userId || item.targetId;
      if (!publicKey || !targetUserId) continue;
      const encryptedKey = await AppCrypto.encryptChatKeyForPublicKey(chatKey.keyBase64, publicKey);
      entries.push({
        userId: state.userId,
        userTargetId: targetUserId,
        publicKey: encryptedKey,
        encryptName: chatKey.encryptName
      });
    }

    if (!entries.length) {
      throw new Error('Сервер не вернул публичные ключи участников');
    }

    await AppApi.sendEncryptMessageKey(entries);
    AppStorage.setChatKey(chatStorageKey, chatKey);
    return chatKey;
  }

  async function resolveChatMemberIds(chat) {
    const currentUser = state.userId;
    if (chat.targetUserId) {
      return [currentUser, chat.targetUserId];
    }
    if (Array.isArray(chat.userIds) && chat.userIds.length) {
      return chat.userIds;
    }
    if (Array.isArray(chat.users) && chat.users.length) {
      return chat.users.map((u) => u.userId || u.id).filter(Boolean);
    }
    if (chat.companionId) {
      return [currentUser, chat.companionId];
    }
    return [currentUser];
  }

  async function normalizeMessages(messages, fallbackChatId) {
    const chatStorageKey = String(fallbackChatId);
    const chatKey = AppStorage.getChatKey(chatStorageKey);
    const result = [];

    for (const item of messages) {
      const normalized = {
        ...item,
        chatId: item.chatId || fallbackChatId,
        messageId: item.messageId || item.id || crypto.randomUUID(),
        senderId: item.senderId || item.userId || item.fromUserId || 'unknown',
        createdAt: item.createdAt || item.sendAt || new Date().toISOString()
      };

      const cipher = item.message;
      if (typeof cipher === 'string' && chatKey?.keyBase64) {
        try {
          normalized.plainText = await AppCrypto.decryptTextWithChatKey(cipher, chatKey.keyBase64);
        } catch {
          normalized.plainText = '[encrypted]';
        }
      } else {
        normalized.plainText = item.messageText || item.text || '[encrypted]';
      }
      result.push(normalized);
    }

    result.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    return result;
  }

  async function normalizeOutgoingMessage(response, plainText) {
    if (response && typeof response === 'object') {
      return {
        ...response,
        senderId: response.senderId || state.userId,
        plainText,
        createdAt: response.createdAt || new Date().toISOString(),
        messageId: response.messageId || crypto.randomUUID(),
        chatId: response.chatId || state.activeChat.chatId
      };
    }
    return {
      messageId: crypto.randomUUID(),
      chatId: state.activeChat.chatId,
      senderId: state.userId,
      plainText,
      createdAt: new Date().toISOString()
    };
  }

  function mergeMessages(older, existing) {
    const map = new Map();
    [...older, ...existing].forEach((msg) => map.set(String(msg.messageId), msg));
    return [...map.values()].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
  }

  function ensureActiveChat() {
    if (!state.activeChat) {
      throw new Error('Сначала выбери чат');
    }
  }
})();
