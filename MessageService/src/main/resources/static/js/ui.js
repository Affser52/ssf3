window.AppUI = (() => {
  const state = { chatClickHandler: null, searchClickHandler: null };

  const el = {
    chatList: () => document.getElementById('chatList'),
    messages: () => document.getElementById('messagesContainer'),
    searchResults: () => document.getElementById('searchResults'),
    activeChatTitle: () => document.getElementById('activeChatTitle'),
    activeChatMeta: () => document.getElementById('activeChatMeta'),
    toastContainer: () => document.getElementById('toastContainer'),
    loadMoreWrap: () => document.getElementById('loadMoreWrap'),
    connectionBadge: () => document.getElementById('connectionBadge')
  };

  function showToast(message, type = 'error') {
    const node = document.createElement('div');
    node.className = `toast ${type === 'success' ? 'success' : ''}`;
    node.textContent = message;
    el.toastContainer().appendChild(node);
    setTimeout(() => node.remove(), 4500);
  }

  function setConnectionState(isOnline) {
    const badge = el.connectionBadge();
    badge.textContent = `WS: ${isOnline ? 'online' : 'offline'}`;
    badge.className = `badge ${isOnline ? 'badge-online' : 'badge-offline'}`;
  }

  function renderChats(chats, activeChatId) {
    const root = el.chatList();
    root.innerHTML = '';
    if (!Array.isArray(chats) || chats.length === 0) {
      root.innerHTML = '<div class="empty-state">Нет чатов</div>';
      return;
    }

    chats.forEach((chat) => {
      const item = document.createElement('div');
      item.className = `chat-item ${String(chat.chatId) === String(activeChatId) ? 'active' : ''}`;
      item.dataset.chatId = chat.chatId;
      item.innerHTML = `
        <div class="chat-name">${escapeHtml(chat.title || chat.username || 'Без названия')}</div>
        <div class="chat-subtitle">${escapeHtml(chat.chatType || chat.type || '')}</div>
      `;
      item.addEventListener('click', () => state.chatClickHandler?.(chat));
      root.appendChild(item);
    });
  }

  function renderSearchResults(items) {
    const root = el.searchResults();
    root.innerHTML = '';
    if (!items?.length) {
      root.classList.add('hidden');
      return;
    }
    items.forEach((item) => {
      const node = document.createElement('div');
      node.className = 'search-item';
      node.innerHTML = `
        <div class="chat-name">${escapeHtml(item.username || item.title || 'Пользователь')}</div>
        <div class="chat-subtitle">${escapeHtml(item.chatType || 'user')}</div>
      `;
      node.addEventListener('click', () => state.searchClickHandler?.(item));
      root.appendChild(node);
    });
    root.classList.remove('hidden');
  }

  function clearSearchResults() {
    el.searchResults().innerHTML = '';
    el.searchResults().classList.add('hidden');
  }

  function setActiveChat(chat) {
    el.activeChatTitle().textContent = chat?.title || chat?.username || 'Выбери чат';
    el.activeChatMeta().textContent = chat ? `chatId: ${chat.chatId || 'new'}` : 'История загружается через HTTP API';
  }

  function renderMessages(messages, currentUserId) {
    const root = el.messages();
    root.innerHTML = '';
    if (!messages?.length) {
      root.innerHTML = '<div class="empty-state">Сообщений пока нет</div>';
      return;
    }
    messages.forEach((msg) => root.appendChild(createMessageNode(msg, currentUserId)));
  }

  function prependMessages(messages, currentUserId) {
    const root = el.messages();
    const previousHeight = root.scrollHeight;
    const fragment = document.createDocumentFragment();
    messages.forEach((msg) => fragment.appendChild(createMessageNode(msg, currentUserId)));
    root.prepend(fragment);
    root.scrollTop = root.scrollHeight - previousHeight;
  }

  function appendMessage(message, currentUserId) {
    const root = el.messages();
    if (root.querySelector('.empty-state')) root.innerHTML = '';
    root.appendChild(createMessageNode(message, currentUserId));
    root.scrollTop = root.scrollHeight;
  }

  function createMessageNode(message, currentUserId) {
    const node = document.createElement('div');
    const senderId = message.senderId || message.userId || message.fromUserId;
    node.className = `message-row ${String(senderId) === String(currentUserId) ? 'own' : ''}`;
    const createdAt = message.createdAt || message.sendAt || '';
    node.innerHTML = `
      <div class="message-meta">${escapeHtml(senderId || 'unknown')} ${createdAt ? '• ' + escapeHtml(formatDate(createdAt)) : ''}</div>
      <div class="message-body">${escapeHtml(message.plainText || message.messageText || message.decryptedText || '[encrypted]')}</div>
    `;
    return node;
  }

  function toggleLoadMore(show) {
    el.loadMoreWrap().classList.toggle('hidden', !show);
  }

  function scrollToBottom() {
    const root = el.messages();
    root.scrollTop = root.scrollHeight;
  }

  function formatDate(value) {
    try { return new Date(value).toLocaleString('ru-RU'); } catch { return value; }
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  return {
    onChatClick: (fn) => { state.chatClickHandler = fn; },
    onSearchClick: (fn) => { state.searchClickHandler = fn; },
    showToast,
    setConnectionState,
    renderChats,
    renderSearchResults,
    clearSearchResults,
    setActiveChat,
    renderMessages,
    prependMessages,
    appendMessage,
    toggleLoadMore,
    scrollToBottom
  };
})();
