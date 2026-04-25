export function normalizeSearch(value) {
  return String(value || '').trim().toLocaleLowerCase('ru-RU');
}

export function matchesLocalChat(chat, query, selfUserId) {
  if (!query || !chat) {
    return false;
  }

  if (chat.chatType === 'GROUP') {
    return normalizeSearch(chat.title).includes(query);
  }

  const directCandidates = [
    chat.title,
    chat.counterpartUsername,
    ...(chat.participants || [])
      .filter((item) => String(item.userId) !== String(selfUserId))
      .map((item) => item.username)
  ];

  return directCandidates.some((value) => normalizeSearch(value).includes(query));
}

export function buildSearchResults({ chats, remoteUsers, query, selfUserId, unreadCounts }) {
  const normalized = normalizeSearch(query);
  const localMatches = (Array.isArray(chats) ? chats : [])
    .filter((chat) => matchesLocalChat(chat, normalized, selfUserId))
    .map((chat) => ({ ...chat, kind: 'existing-chat' }));

  const existingPersonalByUserId = new Set(
    (Array.isArray(chats) ? chats : [])
      .filter((chat) => chat.chatType === 'PERSONAL')
      .map((chat) => String(chat.counterpartUserId || ''))
      .filter(Boolean)
  );

  const existingPersonalByUsername = new Set(
    (Array.isArray(chats) ? chats : [])
      .filter((chat) => chat.chatType === 'PERSONAL')
      .flatMap((chat) => [
        normalizeSearch(chat.title),
        normalizeSearch(chat.counterpartUsername),
        ...(chat.participants || [])
          .filter((item) => String(item.userId) !== String(selfUserId))
          .map((item) => normalizeSearch(item.username))
      ])
      .filter(Boolean)
  );

  const usersWithoutChat = [];
  for (const user of Array.isArray(remoteUsers) ? remoteUsers : []) {
    const userId = String(user?.id || user?.userId || '');
    const username = String(user?.username || '').trim();
    if (!username || !normalizeSearch(username).includes(normalized)) {
      continue;
    }
    if (userId && String(userId) === String(selfUserId)) {
      continue;
    }
    if ((userId && existingPersonalByUserId.has(userId)) || existingPersonalByUsername.has(normalizeSearch(username))) {
      continue;
    }

    usersWithoutChat.push({
      chatId: null,
      chatType: 'PERSONAL',
      title: username,
      avatarUrl: user?.avatarUrl || '',
      counterpartUserId: userId || null,
      counterpartUsername: username,
      counterpartOnline: Boolean(user?.online),
      memberCount: 1,
      onlineCount: user?.online ? 1 : 0,
      participants: [],
      previewText: 'Нажмите, чтобы открыть личный диалог.',
      unreadCount: 0,
      kind: 'user'
    });
  }

  return [
    ...localMatches,
    ...usersWithoutChat
  ].map((item) => ({
    ...item,
    unreadCount: item.chatId ? (unreadCounts?.get(String(item.chatId)) || 0) : 0
  }));
}
