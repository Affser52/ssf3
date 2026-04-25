const DEFAULT_AVATAR = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 96 96'%3E%3Crect width='96' height='96' rx='28' fill='%23d7ebff'/%3E%3Cpath d='M20 36 34 18l10 20M76 36 62 18 52 38' fill='%2377aee9'/%3E%3Crect x='24' y='34' width='48' height='44' rx='22' fill='%23f7fbff' stroke='%236aa0d9' stroke-width='4'/%3E%3Ccircle cx='38' cy='52' r='4' fill='%2335577a'/%3E%3Ccircle cx='58' cy='52' r='4' fill='%2335577a'/%3E%3Cpath d='M42 63c4-3 8-3 12 0' stroke='%2335577a' stroke-width='4' stroke-linecap='round' fill='none'/%3E%3C/svg%3E";

export class ChatUi {
  constructor() {
    this.searchMode = false;
    this.statusItems = [];
    this.toastTimers = new Map();
    this.lastToastAt = new Map();
    this.nodes = {
      settingsBtn: document.getElementById('settingsBtn'),
      catPattern: document.getElementById('catPattern'),
      settingsIcon: document.getElementById('settingsIcon'),
      settingsLabel: document.getElementById('settingsLabel'),
      searchInput: document.getElementById('searchInput'),
      searchBtn: document.getElementById('searchBtn'),
      searchResult: document.getElementById('searchResult'),
      chatList: document.getElementById('chatList'),
      sidebarTitle: document.getElementById('sidebarTitle'),
      sidebarHint: document.getElementById('sidebarHint'),
      createChatBtn: document.getElementById('createChatBtn'),
      createChatModal: document.getElementById('createChatModal'),
      createChatTitleInput: document.getElementById('createChatTitleInput'),
      createChatContinueBtn: document.getElementById('createChatContinueBtn'),
      createChatCloseBtn: document.getElementById('createChatCloseBtn'),
      userId: document.getElementById('userId'),
      wsState: document.getElementById('wsState'),
      keyState: document.getElementById('keyState'),
      chatHeader: document.getElementById('chatHeader'),
      mobileBackBtn: document.getElementById('mobileBackBtn'),
      chatContent: document.getElementById('chatContent'),
      activeChatAvatar: document.getElementById('activeChatAvatar'),
      chatTitle: document.getElementById('chatTitle'),
      chatSubtitle: document.getElementById('chatSubtitle'),
      chatPresence: document.getElementById('chatPresence'),
      chatPresenceDot: document.getElementById('chatPresenceDot'),
      chatMenuBtn: document.getElementById('chatMenuBtn'),
      chatMenu: document.getElementById('chatMenu'),
      messageList: document.getElementById('messageList'),
      scrollBottomBtn: document.getElementById('scrollBottomBtn'),
      emptyChatState: document.getElementById('emptyChatState'),
      messageInput: document.getElementById('messageInput'),
      sendBtn: document.getElementById('sendBtn'),
      fileAttachBtn: document.getElementById('fileAttachBtn'),
      fileInput: document.getElementById('fileInput'),
      fileUploadStatus: document.getElementById('fileUploadStatus'),
      composerHint: document.getElementById('composerHint'),
      membersList: document.getElementById('membersList'),
      membersTitle: document.getElementById('membersTitle'),
      membersHint: document.getElementById('membersHint'),
      memberInput: document.getElementById('memberInput'),
      memberSuggestions: document.getElementById('memberSuggestions'),
      addMemberBtn: document.getElementById('addMemberBtn'),
      removeMemberBtn: document.getElementById('removeMemberBtn'),
      blockMemberBtn: document.getElementById('blockMemberBtn'),
      chatOptionsPanel: document.getElementById('chatOptionsPanel'),
      chatOptionsCloseBtn: document.getElementById('chatOptionsCloseBtn'),
      statusList: document.getElementById('statusList'),
      reloadBtn: document.getElementById('reloadBtn'),
      bootstrapKeysBtn: document.getElementById('bootstrapKeysBtn'),
      wsReconnectBtn: document.getElementById('wsReconnectBtn'),
      projectInfoBtn: document.getElementById('projectInfoBtn'),
      projectInfoModal: document.getElementById('projectInfoModal'),
      projectInfoCloseBtn: document.getElementById('projectInfoCloseBtn'),
      logoutBtn: document.getElementById('logoutBtn'),
      groupTitleInput: document.getElementById('groupTitleInput'),
      createGroupBtn: document.getElementById('createGroupBtn'),
      composer: document.getElementById('composer'),
      servicePanelToggle: document.getElementById('servicePanelToggle'),
      messageMenu: document.getElementById('messageMenu'),
      userProfilePopover: document.getElementById('userProfilePopover'),
      toastStack: this.#createToastStack()
    };
    this.#bindGlobalErrorNotifications();
    this.#renderCatPattern();
  }

  bind(events) {
    this.events = events;

    this.nodes.settingsBtn.addEventListener('click', () => {
      if (this.searchMode) {
        events.onExitSearch?.();
      } else {
        events.onOpenSettings?.();
      }
    });

    this.nodes.searchBtn.addEventListener('click', () => events.onSearchUser?.());
    this.nodes.createChatBtn.addEventListener('click', () => this.openCreateChatModal());
    this.nodes.createChatCloseBtn.addEventListener('click', () => this.closeCreateChatModal());
    this.nodes.createChatModal.addEventListener('click', (event) => {
      if (event.target === this.nodes.createChatModal) {
        this.closeCreateChatModal();
      }
    });
    this.nodes.createChatContinueBtn.addEventListener('click', () => events.onCreateGroupChatModal?.());
    this.nodes.createChatTitleInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        events.onCreateGroupChatModal?.();
      }
    });
    this.nodes.searchInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        events.onSearchUser?.();
      }
    });

    this.nodes.sendBtn.addEventListener('click', () => events.onSendMessage?.());
    this.nodes.fileAttachBtn?.addEventListener('click', () => this.nodes.fileInput?.click());
    this.nodes.fileInput?.addEventListener('change', () => {
      const file = this.nodes.fileInput.files?.[0] || null;
      if (file) {
        events.onAttachFile?.(file);
      }
      this.nodes.fileInput.value = '';
    });
    this.nodes.messageInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        events.onSendMessage?.();
      }
    });

    this.nodes.createGroupBtn?.addEventListener('click', () => events.onCreateGroupChat?.());
    this.nodes.chatMenuBtn.addEventListener('click', () => events.onToggleChatMenu?.());
    this.nodes.mobileBackBtn?.addEventListener('click', () => this.showMobileChatList());
    this.nodes.chatOptionsCloseBtn.addEventListener('click', () => this.setOptionsPanelOpen(false));
    this.nodes.addMemberBtn.addEventListener('click', () => events.onMemberAction?.('add'));
    this.nodes.removeMemberBtn.addEventListener('click', () => events.onMemberAction?.('remove'));
    this.nodes.blockMemberBtn.addEventListener('click', () => events.onMemberAction?.('block'));
    this.nodes.reloadBtn.addEventListener('click', () => events.onReload?.());
    this.nodes.bootstrapKeysBtn.addEventListener('click', () => events.onBootstrapKeys?.());
    this.nodes.wsReconnectBtn.addEventListener('click', () => events.onWsReconnect?.());
    this.nodes.projectInfoBtn?.addEventListener('click', () => this.openProjectInfo());
    this.nodes.projectInfoCloseBtn?.addEventListener('click', () => this.closeProjectInfo());
    this.nodes.projectInfoModal?.addEventListener('click', (event) => {
      if (event.target === this.nodes.projectInfoModal) {
        this.closeProjectInfo();
      }
    });
    this.nodes.logoutBtn.addEventListener('click', () => events.onLogout?.());
    this.nodes.servicePanelToggle.addEventListener('click', () => events.onOpenSettings?.('actions'));

    this.nodes.messageList.addEventListener('contextmenu', (event) => {
      const card = event.target.closest('[data-message-id]');
      if (!card) {
        return;
      }
      event.preventDefault();
      const payload = {
        messageId: Number(card.dataset.messageId),
        senderId: card.dataset.senderId,
        mine: card.dataset.mine === 'true'
      };
      events.onMessageContext?.({ x: event.clientX, y: event.clientY, payload });
    });

    this.nodes.messageList.addEventListener('click', (event) => {
      if (this.#handleProfileTriggerClick(event)) {
        return;
      }

      const button = event.target.closest('[data-file-id]');
      if (!button) {
        return;
      }
      events.onOpenFile?.(button.dataset.fileId);
    });

    this.nodes.chatList.addEventListener('click', (event) => this.#handleProfileTriggerClick(event), true);
    this.nodes.searchResult.addEventListener('click', (event) => this.#handleProfileTriggerClick(event), true);
    this.nodes.membersList.addEventListener('click', (event) => this.#handleProfileTriggerClick(event), true);
    this.nodes.chatHeader.addEventListener('click', (event) => this.#handleProfileTriggerClick(event), true);

    this.nodes.memberInput?.addEventListener('input', () => {
      this.nodes.memberInput.dataset.selectedUserId = '';
      this.nodes.memberInput.dataset.selectedUsername = '';
      events.onMemberSearch?.(this.nodes.memberInput.value);
    });
    this.nodes.memberInput?.addEventListener('focus', () => {
      events.onMemberSearch?.(this.nodes.memberInput.value);
    });

    this.nodes.messageList.addEventListener('scroll', () => this.#updateScrollBottomButton());
    this.nodes.scrollBottomBtn.addEventListener('click', () => this.scrollMessagesToBottom());

    document.addEventListener('click', (event) => {
      if (!event.target.closest('.menu') && !event.target.closest('.icon-button--menu')) {
        this.hideChatMenu();
        this.hideMessageMenu();
      }
      if (!event.target.closest('.user-profile-popover') && !event.target.closest('[data-user-profile-id]')) {
        this.hideUserProfilePopover();
      }
    });
  }

  setUserId(userId) {
    const full = userId || '-';
    this.nodes.userId.textContent = this.#shortId(full);
    this.nodes.userId.title = full;
  }

  setWsOnline(online) {
    this.nodes.wsState.textContent = online ? 'в сети' : 'offline';
    this.nodes.wsState.className = `meta-pill__value ${online ? 'is-good good' : 'is-bad bad'}`;
  }

  setKeyState(text, ok) {
    this.nodes.keyState.textContent = text;
    this.nodes.keyState.className = `meta-pill__value ${ok ? 'is-good good' : 'is-bad bad'}`;
  }

  appendStatus(text, type = 'info') {
    this.statusItems.unshift({
      text,
      type,
      time: new Date()
    });
    this.statusItems = this.statusItems.slice(0, 40);
    this.#renderStatusItems();
    if (this.#shouldShowToast(type)) {
      this.notify(text, type);
    }
  }

  notify(text, type = 'info', timeoutMs = null) {
    const message = String(text || '').trim();
    if (!message || !this.nodes.toastStack) {
      return;
    }

    const normalizedType = this.#normalizeNoticeType(type);
    const now = Date.now();
    const duplicateKey = `${normalizedType}:${message}`;
    const lastShownAt = this.lastToastAt.get(duplicateKey) || 0;
    if (now - lastShownAt < 1200) {
      return;
    }
    this.lastToastAt.set(duplicateKey, now);

    const toast = document.createElement('article');
    toast.className = `toast toast--${normalizedType}`;
    toast.setAttribute('role', normalizedType === 'error' ? 'alert' : 'status');
    toast.innerHTML = `
      <div class="toast__mark">${this.#toastMark(normalizedType)}</div>
      <div class="toast__body">
        <div class="toast__title">${this.#escapeHtml(this.#toastTitle(normalizedType))}</div>
        <div class="toast__text">${this.#escapeHtml(message)}</div>
      </div>
      <button class="toast__close" type="button" aria-label="Закрыть уведомление">×</button>
    `;

    const close = () => this.#removeToast(toast);
    toast.querySelector('.toast__close')?.addEventListener('click', close);
    this.nodes.toastStack.appendChild(toast);

    const maxToasts = 5;
    while (this.nodes.toastStack.children.length > maxToasts) {
      this.#removeToast(this.nodes.toastStack.firstElementChild, false);
    }

    const lifetime = timeoutMs ?? (normalizedType === 'error' ? 9000 : 5200);
    const timer = window.setTimeout(close, lifetime);
    this.toastTimers.set(toast, timer);
  }

  setSearchMode(active) {
    this.searchMode = Boolean(active);
    this.nodes.chatList.hidden = this.searchMode;
    this.nodes.searchResult.hidden = !this.searchMode;
    this.nodes.settingsLabel.textContent = this.searchMode ? 'назад' : 'настройки';
    this.nodes.settingsIcon.textContent = this.searchMode ? '←' : '⚙';
    this.nodes.sidebarTitle.textContent = this.searchMode ? 'Результаты поиска' : 'Ваши чаты';
    this.nodes.sidebarHint.textContent = this.searchMode
      ? 'Сначала идут ваши диалоги и группы, затем люди без открытого чата.'
      : 'Личные диалоги и группы с последней активностью.';
  }

  clearSearchResults(message = 'Введите запрос, чтобы найти чат, группу или человека.') {
    this.nodes.searchResult.innerHTML = `<div class="empty-state empty-state--compact">${this.#escapeHtml(message)}</div>`;
  }

  renderSearchResults(results, onSelect) {
    this.setSearchMode(true);
    this.nodes.searchResult.innerHTML = '';
    if (!Array.isArray(results) || results.length === 0) {
      this.clearSearchResults('Ничего не найдено. Попробуйте другой ник или название.');
      return;
    }

    const groups = this.#groupResults(results);
    groups.forEach((group) => {
      const block = document.createElement('section');
      block.className = 'search-section';
      block.innerHTML = `<div class="search-section__title">${this.#escapeHtml(group.title)}</div>`;
      group.items.forEach((item) => {
        const row = this.#buildSidebarCard(item, false);
        row.classList.add('search-card');
        row.addEventListener('click', () => onSelect(item));
        block.appendChild(row);
      });
      this.nodes.searchResult.appendChild(block);
    });
  }

  renderChats(chats, activeChatId, onClickChat) {
    const previousScrollTop = this.nodes.chatList.scrollTop;
    this.nodes.chatList.innerHTML = '';
    if (!Array.isArray(chats) || chats.length === 0) {
      this.nodes.chatList.innerHTML = '<div class="empty-state empty-state--compact">Пока нет диалогов. Найдите собеседника или создайте группу в настройках.</div>';
      return;
    }

    const fragment = document.createDocumentFragment();
    chats.forEach((chat) => {
      const card = this.#buildSidebarCard(chat, String(chat.chatId) === String(activeChatId));
      card.addEventListener('click', () => onClickChat(chat.chatId));
      fragment.appendChild(card);
    });
    this.nodes.chatList.appendChild(fragment);
    this.nodes.chatList.scrollTop = previousScrollTop;
  }

  setActiveChatInList(chatId) {
    Array.from(this.nodes.chatList.querySelectorAll('.dialog-card')).forEach((card) => {
      const active = String(card.dataset.chatId) === String(chatId);
      card.classList.toggle('is-active', active);
      card.classList.toggle('active', active);
    });
  }

  setChatUnreadCount(chatId, count) {
    const card = Array.from(this.nodes.chatList.querySelectorAll('.dialog-card'))
      .find((item) => String(item.dataset.chatId) === String(chatId));
    if (!card) {
      return;
    }

    const unreadBadge = card.querySelector('.dialog-card__unread');
    if (!unreadBadge) {
      return;
    }

    const normalized = Math.max(0, Math.floor(Number(count) || 0));
    unreadBadge.textContent = this.#unreadLabel(normalized);
    unreadBadge.hidden = normalized <= 0;
    card.classList.toggle('has-unread', normalized > 0);
  }

  renderActiveChat(chat) {
    if (!chat) {
      this.nodes.chatHeader.hidden = true;
      this.nodes.composer.hidden = true;
      this.nodes.activeChatAvatar.src = DEFAULT_AVATAR;
      this.nodes.chatTitle.textContent = 'Выберите чат';
      this.nodes.chatTitle.title = 'Чат еще не выбран';
      this.#clearProfileTrigger(this.nodes.activeChatAvatar);
      this.#clearProfileTrigger(this.nodes.chatTitle);
      this.nodes.chatSubtitle.textContent = 'Сообщения появятся здесь после выбора диалога.';
      this.nodes.chatPresence.textContent = '';
      this.nodes.chatPresenceDot.className = 'presence-dot';
      this.nodes.chatMenuBtn.disabled = true;
      this.nodes.messageInput.disabled = true;
      this.nodes.sendBtn.disabled = true;
      if (this.nodes.fileAttachBtn) {
        this.nodes.fileAttachBtn.disabled = true;
      }
      this.nodes.messageInput.placeholder = 'Сначала выберите чат';
      this.nodes.emptyChatState.hidden = false;
      this.nodes.messageList.innerHTML = '';
      this.setOptionsPanelOpen(false);
      this.showMobileChatList();
      return;
    }

    this.nodes.chatHeader.hidden = false;
    this.nodes.composer.hidden = false;
    this.nodes.activeChatAvatar.src = chat.avatarUrl || DEFAULT_AVATAR;
    this.nodes.chatTitle.textContent = chat.title || `Чат #${chat.chatId}`;
    this.nodes.chatTitle.title = chat.counterpartUserId || chat.chatId || '';
    const profileTarget = this.#chatProfileTarget(chat);
    this.#setProfileTrigger(this.nodes.activeChatAvatar, profileTarget);
    this.#setProfileTrigger(this.nodes.chatTitle, profileTarget);
    this.nodes.chatSubtitle.textContent = chat.chatType === 'GROUP'
      ? `${chat.memberCount || 0} участников`
      : 'Личный диалог';
    this.nodes.chatPresence.textContent = this.#presenceLabel(chat);
    this.nodes.chatPresenceDot.className = `presence-dot ${this.#presenceClass(chat)}`;
    this.nodes.chatMenuBtn.disabled = false;
    this.nodes.messageInput.disabled = false;
    this.nodes.sendBtn.disabled = false;
    if (this.nodes.fileAttachBtn) {
      this.nodes.fileAttachBtn.disabled = false;
    }
    this.nodes.messageInput.placeholder = 'Напишите сообщение';
    this.nodes.emptyChatState.hidden = true;
    this.showMobileChat();
  }

  showMobileChat() {
    document.body.classList.add('mobile-chat-open');
  }

  showMobileChatList() {
    document.body.classList.remove('mobile-chat-open');
    this.hideChatMenu();
    this.hideMessageMenu();
    this.setOptionsPanelOpen(false);
  }

  renderMessages(messages, selfUserId, chat = null, options = {}) {
    const previousScrollTop = this.nodes.messageList.scrollTop;
    const previousScrollHeight = this.nodes.messageList.scrollHeight;
    const wasNearBottom = this.#isMessagesNearBottom();

    this.nodes.messageList.innerHTML = '';
    this.nodes.scrollBottomBtn.hidden = true;
    if (!Array.isArray(messages) || messages.length === 0) {
      if (this.nodes.chatTitle.textContent !== 'Выберите чат') {
        this.nodes.messageList.innerHTML = '<div class="empty-state">Сообщений пока нет. Начните разговор первым.</div>';
      }
      return;
    }

    const fragment = document.createDocumentFragment();
    let previousDateKey = null;
    messages.forEach((message) => {
      const dateKey = this.#dateKey(message.createdAt);
      if (dateKey !== previousDateKey) {
        previousDateKey = dateKey;
        const divider = document.createElement('div');
        divider.className = 'message-date-divider';
        divider.innerHTML = `<span>${this.#escapeHtml(this.#formatDate(message.createdAt))}</span>`;
        fragment.appendChild(divider);
      }

      if (message.kind === 'system') {
        const row = document.createElement('div');
        row.className = 'system-message-row';
        row.innerHTML = `<span>${this.#escapeHtml(message.text || '')}</span>`;
        fragment.appendChild(row);
        return;
      }

      const mine = String(message.senderId) === String(selfUserId);
      const author = this.#messageAuthor(message, selfUserId, chat);
      const card = document.createElement('article');
      card.className = `message-card message ${mine ? 'is-mine mine' : 'is-other other'}`;
      if (message.kind !== 'file') {
        card.dataset.messageId = String(message.messageId);
      }
      card.dataset.senderId = String(message.senderId || '');
      card.dataset.mine = String(mine);
      const authorAttrs = this.#profileAttributes(author);
      card.innerHTML = `
        <div class="message-card__avatar user-profile-trigger" ${authorAttrs}>${this.#avatarMarkup(author.avatarUrl, author.name)}</div>
        <div class="message-card__bubble">
          <div class="message-card__meta">
            <span class="message-card__author user-profile-trigger" ${authorAttrs}>${this.#escapeHtml(author.name)}</span>
            <time>${this.#formatTime(message.createdAt)}</time>
          </div>
          ${message.kind === 'file' ? this.#fileMarkup(message) : `<div class="message-card__text">${this.#escapeHtml(message.text || '')}</div>`}
        </div>
      `;
      fragment.appendChild(card);
    });
    this.nodes.messageList.appendChild(fragment);

    if (options.stickToBottom || (!options.preserveScroll && wasNearBottom)) {
      this.scrollMessagesToBottom();
      return;
    }

    if (options.preserveScroll) {
      const nextScrollHeight = this.nodes.messageList.scrollHeight;
      this.nodes.messageList.scrollTop = previousScrollTop + (nextScrollHeight - previousScrollHeight);
    } else {
      this.nodes.messageList.scrollTop = previousScrollTop;
    }
    this.#updateScrollBottomButton();
  }

  scrollMessagesToBottom() {
    this.nodes.messageList.scrollTop = this.nodes.messageList.scrollHeight;
    this.#updateScrollBottomButton();
  }

  renderMembers(members, chat) {
    this.nodes.membersList.innerHTML = '';
    this.clearMemberSelection();
    this.nodes.membersTitle.textContent = chat?.chatType === 'GROUP' ? 'Участники чата' : 'Собеседник';
    this.nodes.membersHint.textContent = chat?.chatType === 'GROUP'
      ? 'Добавляйте, удаляйте или блокируйте участников, если роль это позволяет.'
      : 'В личном диалоге можно посмотреть собеседника и при необходимости заблокировать его в этом чате.';

    const canManage = Boolean(chat?.canManageMembers);
    const isPrivate = chat?.chatType === 'PERSONAL';

    this.nodes.memberInput.disabled = isPrivate || !canManage;
    this.nodes.addMemberBtn.hidden = isPrivate || !canManage;
    this.nodes.removeMemberBtn.hidden = isPrivate || !canManage;
    this.nodes.blockMemberBtn.textContent = isPrivate ? 'Заблокировать пользователя' : 'Заблокировать в чате';
    this.nodes.blockMemberBtn.hidden = !(isPrivate || canManage);

    if (!Array.isArray(members) || members.length === 0) {
      this.nodes.membersList.innerHTML = '<div class="empty-state empty-state--compact">Список участников пока пуст.</div>';
      return;
    }

    members.forEach((member) => {
      const row = document.createElement('div');
      row.className = 'member-row user-profile-trigger';
      this.#setProfileTrigger(row, this.#memberProfileTarget(member));
      row.innerHTML = `
        <div class="member-row__avatar">${this.#avatarMarkup(member.avatarUrl, member.username)}</div>
        <div class="member-row__body">
          <div class="member-row__name" title="${this.#escapeHtml(member.userId || '')}">${this.#escapeHtml(member.username || this.#shortId(member.userId))}</div>
          <div class="member-row__meta">${member.online ? 'в сети' : 'offline'}</div>
        </div>
      `;
      this.nodes.membersList.appendChild(row);
    });
  }

  renderMemberSuggestions(users, onSelect) {
    if (!this.nodes.memberSuggestions) {
      return;
    }

    const rows = Array.isArray(users) ? users.slice(0, 10) : [];
    if (rows.length === 0) {
      this.clearMemberSuggestions();
      return;
    }

    this.nodes.memberSuggestions.innerHTML = '';
    rows.forEach((user) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'member-suggestion';
      button.innerHTML = `
        <span class="member-suggestion__avatar">${this.#avatarMarkup(user.avatarUrl, user.username)}</span>
        <span class="member-suggestion__body">
          <span class="member-suggestion__name">${this.#escapeHtml(user.username || this.#shortId(user.id || user.userId))}</span>
          <span class="member-suggestion__meta">${user.online ? 'в сети' : 'offline'}</span>
        </span>
      `;
      button.addEventListener('click', () => {
        this.setMemberSelection(user);
        this.clearMemberSuggestions();
        onSelect?.(user);
      });
      this.nodes.memberSuggestions.appendChild(button);
    });

    this.nodes.memberSuggestions.hidden = false;
  }

  clearMemberSuggestions() {
    if (!this.nodes.memberSuggestions) {
      return;
    }
    this.nodes.memberSuggestions.hidden = true;
    this.nodes.memberSuggestions.innerHTML = '';
  }

  clearMemberSelection() {
    if (!this.nodes.memberInput) {
      return;
    }
    this.nodes.memberInput.value = '';
    this.nodes.memberInput.dataset.selectedUserId = '';
    this.nodes.memberInput.dataset.selectedUsername = '';
    this.clearMemberSuggestions();
  }

  setMemberSelection(user) {
    if (!this.nodes.memberInput || !user) {
      return;
    }
    const userId = user.id || user.userId || '';
    this.nodes.memberInput.value = user.username || '';
    this.nodes.memberInput.dataset.selectedUserId = String(userId || '');
    this.nodes.memberInput.dataset.selectedUsername = String(user.username || '');
  }

  getSelectedMemberUserId() {
    return this.nodes.memberInput?.dataset?.selectedUserId || '';
  }

  renderUserProfilePopover(profile, anchor, onStartChat) {
    const popover = this.nodes.userProfilePopover;
    if (!popover || !profile) {
      return;
    }

    const userId = profile.id || profile.userId || '';
    const username = profile.username || this.#shortId(userId);
    popover.innerHTML = `
      <div class="user-profile-popover__top">
        <div class="user-profile-popover__avatar">${this.#avatarMarkup(profile.avatarUrl, username)}</div>
        <div class="user-profile-popover__identity">
          <strong>${this.#escapeHtml(username)}</strong>
          <span>${profile.online ? 'в сети' : 'offline'}</span>
        </div>
      </div>
      <div class="user-profile-popover__meta">
        <span>ID</span>
        <code>${this.#escapeHtml(userId)}</code>
      </div>
      ${profile.createdAt ? `
        <div class="user-profile-popover__meta">
          <span>Профиль создан</span>
          <strong>${this.#escapeHtml(this.#formatDate(profile.createdAt))}</strong>
        </div>
      ` : ''}
      <button type="button" class="button button--primary user-profile-popover__chat">Общение</button>
    `;

    popover.querySelector('.user-profile-popover__chat')?.addEventListener('click', () => {
      this.hideUserProfilePopover();
      onStartChat?.(profile);
    });

    popover.hidden = false;
    this.#placeProfilePopover(popover, anchor);
  }

  hideUserProfilePopover() {
    if (this.nodes.userProfilePopover) {
      this.nodes.userProfilePopover.hidden = true;
    }
  }

  renderChatMenu(items) {
    this.nodes.chatMenu.innerHTML = '';
    if (!Array.isArray(items) || items.length === 0) {
      this.hideChatMenu();
      return;
    }

    items.forEach((item) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `menu__item ${item.danger ? 'is-danger' : ''}`;
      button.textContent = item.label;
      button.addEventListener('click', () => {
        this.hideChatMenu();
        item.onClick?.();
      });
      this.nodes.chatMenu.appendChild(button);
    });

    this.nodes.chatMenu.hidden = false;
  }

  hideChatMenu() {
    this.nodes.chatMenu.hidden = true;
  }

  renderMessageMenu(x, y, items, payload) {
    this.nodes.messageMenu.innerHTML = '';
    if (!Array.isArray(items) || items.length === 0) {
      this.hideMessageMenu();
      return;
    }

    items.forEach((item) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `menu__item ${item.danger ? 'is-danger' : ''}`;
      button.textContent = item.label;
      button.addEventListener('click', () => {
        this.hideMessageMenu();
        item.onClick?.(payload);
      });
      this.nodes.messageMenu.appendChild(button);
    });

    this.nodes.messageMenu.hidden = false;
    this.nodes.messageMenu.style.left = `${x}px`;
    this.nodes.messageMenu.style.top = `${y}px`;
  }

  hideMessageMenu() {
    this.nodes.messageMenu.hidden = true;
  }

  setOptionsPanelOpen(open) {
    this.nodes.chatOptionsPanel.hidden = !open;
    this.nodes.chatContent.classList.toggle('has-options-open', Boolean(open));
  }

  setComposerLocked(locked, hint = '') {
    this.nodes.messageInput.disabled = locked;
    this.nodes.sendBtn.disabled = locked;
    if (this.nodes.fileAttachBtn) {
      this.nodes.fileAttachBtn.disabled = locked;
    }
    if (hint && this.nodes.composerHint) {
      this.nodes.composerHint.textContent = hint;
    }
  }

  setFileUploadState(text = '', progress = null) {
    if (!this.nodes.fileUploadStatus) {
      return;
    }
    const message = String(text || '').trim();
    if (!message) {
      this.nodes.fileUploadStatus.hidden = true;
      this.nodes.fileUploadStatus.textContent = '';
      return;
    }
    this.nodes.fileUploadStatus.hidden = false;
    this.nodes.fileUploadStatus.textContent = progress === null ? message : `${message} ${progress}%`;
  }

  openCreateChatModal() {
    this.nodes.createChatModal.hidden = false;
    this.nodes.createChatTitleInput.focus();
  }

  closeCreateChatModal() {
    this.nodes.createChatModal.hidden = true;
  }

  openProjectInfo() {
    if (this.nodes.projectInfoModal) {
      this.nodes.projectInfoModal.hidden = false;
    }
  }

  closeProjectInfo() {
    if (this.nodes.projectInfoModal) {
      this.nodes.projectInfoModal.hidden = true;
    }
  }

  #renderStatusItems() {
    this.nodes.statusList.innerHTML = '';
    if (this.statusItems.length === 0) {
      this.nodes.statusList.innerHTML = '<div class="empty-state empty-state--compact">Событий пока нет.</div>';
      return;
    }

    this.statusItems.forEach((item) => {
      const row = document.createElement('div');
      row.className = `status-entry status-item status-entry--${item.type}`;
      row.innerHTML = `
        <div class="status-entry__time">${item.time.toLocaleTimeString()}</div>
        <div class="status-entry__text">${this.#escapeHtml(item.text)}</div>
      `;
      this.nodes.statusList.appendChild(row);
    });
  }

  #createToastStack() {
    let stack = document.querySelector('.toast-stack');
    if (stack) {
      return stack;
    }

    stack = document.createElement('section');
    stack.className = 'toast-stack';
    stack.setAttribute('aria-live', 'polite');
    stack.setAttribute('aria-label', 'Уведомления');
    document.body.appendChild(stack);
    return stack;
  }

  #bindGlobalErrorNotifications() {
    if (window.__mescatToastsBound) {
      return;
    }
    window.__mescatToastsBound = true;

    window.addEventListener('error', (event) => {
      const message = event?.message || 'Неожиданная ошибка интерфейса.';
      this.appendStatus(message, 'error');
    });

    window.addEventListener('unhandledrejection', (event) => {
      const reason = event?.reason;
      const message = reason?.message || String(reason || 'Неожиданная ошибка операции.');
      this.appendStatus(message, 'error');
    });
  }

  #shouldShowToast(type) {
    const normalizedType = this.#normalizeNoticeType(type);
    return ['error', 'warning', 'ok'].includes(normalizedType);
  }

  #normalizeNoticeType(type) {
    const value = String(type || 'info').toLowerCase();
    if (value === 'warn') {
      return 'warning';
    }
    if (value === 'success') {
      return 'ok';
    }
    if (['error', 'warning', 'ok', 'info'].includes(value)) {
      return value;
    }
    return 'info';
  }

  #toastTitle(type) {
    if (type === 'error') {
      return 'Ошибка';
    }
    if (type === 'warning') {
      return 'Внимание';
    }
    if (type === 'ok') {
      return 'Готово';
    }
    return 'Информация';
  }

  #toastMark(type) {
    if (type === 'error') {
      return '!';
    }
    if (type === 'warning') {
      return '?';
    }
    if (type === 'ok') {
      return '+';
    }
    return 'i';
  }

  #removeToast(toast, animated = true) {
    if (!toast) {
      return;
    }

    const timer = this.toastTimers.get(toast);
    if (timer) {
      window.clearTimeout(timer);
      this.toastTimers.delete(toast);
    }

    if (!animated) {
      toast.remove();
      return;
    }

    toast.classList.add('is-hiding');
    window.setTimeout(() => toast.remove(), 180);
  }

  #buildSidebarCard(chat, active) {
    const card = document.createElement('button');
    const unreadCount = Math.max(0, Math.floor(Number(chat.unreadCount) || 0));
    const profileAttrs = this.#profileAttributes(this.#chatProfileTarget(chat));
    card.type = 'button';
    card.className = `dialog-card chat-item ${active ? 'is-active active' : ''} ${unreadCount > 0 ? 'has-unread' : ''}`;
    card.dataset.chatId = chat.chatId ? String(chat.chatId) : '';
    card.innerHTML = `
      <div class="dialog-card__avatar ${profileAttrs ? 'user-profile-trigger' : ''}" ${profileAttrs}>${this.#avatarMarkup(chat.avatarUrl, chat.title)}</div>
      <div class="dialog-card__body">
        <div class="dialog-card__top">
          <span class="dialog-card__title ${profileAttrs ? 'user-profile-trigger' : ''}" ${profileAttrs}>${this.#escapeHtml(chat.title || `Чат #${chat.chatId || '?'}`)}</span>
          <span class="dialog-card__meta-badges">
            <span class="dialog-card__unread" ${unreadCount > 0 ? '' : 'hidden'}>${this.#escapeHtml(this.#unreadLabel(unreadCount))}</span>
            <span class="dialog-card__badge">${this.#escapeHtml(this.#badgeLabel(chat))}</span>
          </span>
        </div>
        <div class="dialog-card__preview">${this.#escapeHtml(chat.previewText || this.#fallbackPreview(chat))}</div>
        <div class="dialog-card__bottom">
          <span class="dialog-card__status">${this.#escapeHtml(this.#presenceLabel(chat))}</span>
        </div>
      </div>
    `;
    return card;
  }

  #groupResults(results) {
    const existing = results.filter((item) => item.kind === 'existing-chat');
    const users = results.filter((item) => item.kind === 'user');
    const groups = [];
    if (existing.length) {
      groups.push({ title: 'Чаты и группы', items: existing });
    }
    if (users.length) {
      groups.push({ title: 'Люди без открытого диалога', items: users });
    }
    return groups;
  }

  #badgeLabel(chat) {
    if (chat.kind === 'user') {
      return 'новый чат';
    }
    return chat.chatType === 'GROUP' ? 'группа' : 'личный';
  }

  #unreadLabel(count) {
    const value = Math.max(0, Math.floor(Number(count) || 0));
    if (value <= 0) {
      return '';
    }
    return value > 99 ? '99+' : String(value);
  }

  #presenceLabel(chat) {
    if (!chat) {
      return '';
    }
    if (chat.chatType === 'GROUP') {
      return `в сети ${chat.onlineCount || 0} из ${chat.memberCount || 0}`;
    }
    return chat.counterpartOnline ? 'в сети' : 'offline';
  }

  #presenceClass(chat) {
    if (chat?.chatType === 'GROUP') {
      return (chat.onlineCount || 0) > 0 ? 'is-online' : 'is-offline';
    }
    return chat?.counterpartOnline ? 'is-online' : 'is-offline';
  }

  #fallbackPreview(chat) {
    if (chat.kind === 'user') {
      return 'Нажмите, чтобы открыть личный диалог';
    }
    return chat.chatType === 'GROUP' ? 'Групповой чат' : 'Личный диалог';
  }

  #formatTime(value) {
    if (!value) {
      return 'сейчас';
    }
    try {
      return new Date(value).toLocaleTimeString('ru-RU', {
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return 'сейчас';
    }
  }

  #formatDate(value) {
    const date = value ? new Date(value) : new Date();
    if (Number.isNaN(date.getTime())) {
      return 'сегодня';
    }
    return date.toLocaleDateString('ru-RU', {
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    });
  }

  #dateKey(value) {
    const date = value ? new Date(value) : new Date();
    if (Number.isNaN(date.getTime())) {
      return 'unknown';
    }
    return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
  }

  #messageAuthor(message, selfUserId, chat) {
    const participant = (chat?.participants || [])
      .find((item) => String(item.userId) === String(message.senderId));

    if (participant) {
      return {
        userId: participant.userId || message.senderId || '',
        name: participant.username || (String(message.senderId) === String(selfUserId) ? 'Вы' : 'Пользователь'),
        avatarUrl: participant.avatarUrl || '',
        online: Boolean(participant.online)
      };
    }

    return {
      userId: message.senderId || '',
      name: message.senderUsername || (String(message.senderId) === String(selfUserId) ? 'Вы' : 'Пользователь'),
      avatarUrl: message.senderAvatarUrl || '',
      online: null
    };
  }

  #fileMarkup(file) {
    const name = file.originalFileName || 'Файл';
    const size = this.#formatBytes(file.sizeBytes);
    const meta = [
      file.fileType || file.mimeType || 'file',
      size
    ].filter(Boolean).join(' · ');

    if (file.previewUrl && this.#isImageFile(file)) {
      return `
        <div class="message-file message-file--media message-file--image">
          <div class="message-file__media-frame">
            ${size ? `<span class="message-file__size">${this.#escapeHtml(size)}</span>` : ''}
            <button class="message-file__download" type="button" data-file-id="${this.#escapeHtml(file.fileId || '')}" title="Скачать файл">Скачать</button>
            <img class="message-file__image" src="${this.#escapeHtml(file.previewUrl)}" alt="${this.#escapeHtml(name)}" loading="lazy">
          </div>
          <div class="message-file__caption">${this.#escapeHtml(name)}</div>
        </div>
      `;
    }

    if (file.previewUrl && this.#isVideoFile(file)) {
      return `
        <div class="message-file message-file--media message-file--video">
          <div class="message-file__media-frame">
            ${size ? `<span class="message-file__size">${this.#escapeHtml(size)}</span>` : ''}
            <button class="message-file__download" type="button" data-file-id="${this.#escapeHtml(file.fileId || '')}" title="Скачать файл">Скачать</button>
            <video class="message-file__video" src="${this.#escapeHtml(file.previewUrl)}" controls preload="metadata"></video>
          </div>
          <div class="message-file__caption">${this.#escapeHtml(name)}</div>
        </div>
      `;
    }

    if (file.previewUrl && this.#isAudioFile(file)) {
      return `
        <div class="message-file message-file--media message-file--audio">
          <div class="message-file__audio-card">
            ${size ? `<span class="message-file__size message-file__size--audio">${this.#escapeHtml(size)}</span>` : ''}
            <audio class="message-file__audio" src="${this.#escapeHtml(file.previewUrl)}" controls preload="metadata"></audio>
            <button class="message-file__download message-file__download--audio" type="button" data-file-id="${this.#escapeHtml(file.fileId || '')}" title="Скачать файл">Скачать</button>
          </div>
          <div class="message-file__caption">${this.#escapeHtml(name)}</div>
        </div>
      `;
    }

    return `
      <div class="message-file">
        <button class="message-file__button" type="button" data-file-id="${this.#escapeHtml(file.fileId || '')}">
          <span class="message-file__icon">${this.#escapeHtml(this.#fileIcon(file))}</span>
          <span>
            <span class="message-file__name">${this.#escapeHtml(name)}</span>
            <span class="message-file__meta">${this.#escapeHtml(meta)}</span>
          </span>
        </button>
      </div>
    `;
  }

  #isImageFile(file) {
    const type = String(file.fileType || '').toUpperCase();
    const mime = String(file.mimeType || '').toLowerCase();
    return type === 'IMAGE' || mime.startsWith('image/');
  }

  #isVideoFile(file) {
    const type = String(file.fileType || '').toUpperCase();
    const mime = String(file.mimeType || '').toLowerCase();
    return type === 'VIDEO' || mime.startsWith('video/');
  }

  #isAudioFile(file) {
    const type = String(file.fileType || '').toUpperCase();
    const mime = String(file.mimeType || '').toLowerCase();
    return type === 'AUDIO' || mime.startsWith('audio/');
  }
  #fileIcon(file) {
    const type = String(file.fileType || file.mimeType || '').toLowerCase();
    if (type.includes('image')) {
      return 'IMG';
    }
    if (type.includes('video')) {
      return 'VID';
    }
    if (type.includes('audio')) {
      return 'AUD';
    }
    return 'DOC';
  }

  #formatBytes(value) {
    const size = Number(value || 0);
    if (!Number.isFinite(size) || size <= 0) {
      return '';
    }
    const units = ['Б', 'КБ', 'МБ', 'ГБ'];
    let current = size;
    let index = 0;
    while (current >= 1024 && index < units.length - 1) {
      current /= 1024;
      index += 1;
    }
    return `${current.toFixed(current >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
  }

  #updateScrollBottomButton() {
    const list = this.nodes.messageList;
    const distanceFromBottom = list.scrollHeight - list.scrollTop - list.clientHeight;
    this.nodes.scrollBottomBtn.hidden = distanceFromBottom < 420;
  }

  #isMessagesNearBottom() {
    const list = this.nodes.messageList;
    return list.scrollHeight - list.scrollTop - list.clientHeight < 160;
  }

  #avatarMarkup(url, title) {
    if (url) {
      return `<img src="${this.#escapeHtml(url)}" alt="${this.#escapeHtml(title || 'avatar')}" onerror="this.onerror=null;this.src=&quot;${this.#escapeHtml(DEFAULT_AVATAR)}&quot;">`;
    }
    return `<span>${this.#escapeHtml(this.#initials(title))}</span>`;
  }

  #handleProfileTriggerClick(event) {
    const target = event.target.closest('[data-user-profile-id]');
    if (!target) {
      return false;
    }

    const userId = target.dataset.userProfileId;
    if (!userId) {
      return false;
    }

    event.preventDefault();
    event.stopPropagation();
    this.events?.onOpenUserProfile?.(userId, target);
    return true;
  }

  #setProfileTrigger(element, profile) {
    if (!element) {
      return;
    }

    if (!profile?.userId) {
      this.#clearProfileTrigger(element);
      return;
    }

    element.classList.add('user-profile-trigger');
    element.dataset.userProfileId = String(profile.userId);
    element.dataset.userProfileUsername = String(profile.username || '');
    element.dataset.userProfileAvatar = String(profile.avatarUrl || '');
    element.dataset.userProfileOnline = String(Boolean(profile.online));
    element.title = profile.username ? `${profile.username} · ${profile.userId}` : String(profile.userId);
  }

  #clearProfileTrigger(element) {
    if (!element) {
      return;
    }
    element.classList.remove('user-profile-trigger');
    delete element.dataset.userProfileId;
    delete element.dataset.userProfileUsername;
    delete element.dataset.userProfileAvatar;
    delete element.dataset.userProfileOnline;
  }

  #profileAttributes(profile) {
    if (!profile?.userId) {
      return '';
    }

    return [
      `data-user-profile-id="${this.#escapeHtml(profile.userId)}"`,
      `data-user-profile-username="${this.#escapeHtml(profile.username || '')}"`,
      `data-user-profile-avatar="${this.#escapeHtml(profile.avatarUrl || '')}"`,
      `data-user-profile-online="${this.#escapeHtml(String(Boolean(profile.online)))}"`
    ].join(' ');
  }

  #memberProfileTarget(member) {
    if (!member?.userId) {
      return null;
    }

    return {
      userId: member.userId,
      username: member.username || '',
      avatarUrl: member.avatarUrl || '',
      online: Boolean(member.online)
    };
  }

  #chatProfileTarget(chat) {
    if (!chat || chat.chatType !== 'PERSONAL') {
      return null;
    }

    const userId = chat.counterpartUserId || (chat.kind === 'user' ? chat.counterpartUserId : null);
    if (!userId) {
      return null;
    }

    return {
      userId,
      username: chat.counterpartUsername || chat.title || '',
      avatarUrl: chat.avatarUrl || '',
      online: Boolean(chat.counterpartOnline)
    };
  }

  #placeProfilePopover(popover, anchor) {
    const rect = anchor?.getBoundingClientRect?.();
    const gap = 10;
    const width = Math.min(320, window.innerWidth - 24);
    popover.style.width = `${width}px`;

    let left = rect ? rect.right + gap : 20;
    let top = rect ? rect.top : 20;

    if (left + width > window.innerWidth - 12) {
      left = rect ? rect.left - width - gap : window.innerWidth - width - 12;
    }
    if (left < 12) {
      left = 12;
    }

    const height = popover.offsetHeight || 220;
    if (top + height > window.innerHeight - 12) {
      top = Math.max(12, window.innerHeight - height - 12);
    }

    popover.style.left = `${left}px`;
    popover.style.top = `${Math.max(12, top)}px`;
  }

  #shortId(value) {
    const text = String(value || '-');
    if (text.length <= 18) {
      return text;
    }
    return `${text.slice(0, 8)}...${text.slice(-6)}`;
  }

  #initials(value) {
    const text = String(value || '?').trim();
    if (!text) {
      return '?';
    }
    return text.replace(/[^\p{L}\p{N}]+/gu, '').slice(0, 2).toUpperCase() || '?';
  }

  #renderCatPattern() {
    if (!this.nodes.catPattern) {
      return;
    }

    const rows = 24;
    const cols = 28;
    const variants = 10;
    const angles = [-13, 8, -5, 15, -18, 4, 11, -9, 19, -3];
    const sizes = [28, 31, 34, 29, 37, 32, 26, 35, 30, 33];
    const fragment = document.createDocumentFragment();

    this.nodes.catPattern.innerHTML = '';
    for (let row = 0; row < rows; row += 1) {
      for (let col = 0; col < cols; col += 1) {
        const index = (row * 7 + col * 3) % variants;
        const cat = document.createElement('span');
        cat.className = `cat-sprite cat-sprite--${index + 1}`;
        cat.style.left = `${row * 18 + col * 54 + ((row + col) % 3) * 5 - 26}px`;
        cat.style.top = `${row * 48 + ((col % 2) * 6) - 18}px`;
        cat.style.width = `${sizes[index]}px`;
        cat.style.height = `${sizes[index]}px`;
        cat.style.transform = `rotate(${angles[index]}deg)`;
        fragment.appendChild(cat);
      }
    }

    this.nodes.catPattern.appendChild(fragment);
  }

  #escapeHtml(text) {
    return String(text ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
}
