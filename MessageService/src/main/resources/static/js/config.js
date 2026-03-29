window.APP_CONFIG = {
  api: {
    getId: '/getId',
    chats: '/api/chats',
    savePublicKey: '/api/encrypt_key/new_key',
    getPublicKeys: '/api/encrypt_key/',
    sendEncryptMessageKey: '/api/encrypt_message_key/send',
    deleteEncryptMessageKey: '/api/encrypt_message_key/delete',
    getLastMessages: (count) => `/api/getLastMessages/${count}`,
    getMessagesRelative: (messageId, count) => `/api/getMessageInChatWithLimit/${messageId}/${count}`,
    sendMessage: '/api/sendMessage',
    sendMessageAndNewChat: '/api/newMessageAndNewChat',
    searchByUsername: (username) => `/api/search_by_username?username=${encodeURIComponent(username)}`
  },
  ws: {
    enabled: true,
    url: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws',
    sendDestination: '/app/send.chat'
  },
  historyPageSize: 100,
  keyRotationMessageLimit: 200,
  storageKeys: {
    userId: 'mescat.userId',
    userKeys: 'mescat.user.keys',
    chatKeys: 'mescat.chat.keys',
    counters: 'mescat.chat.counters'
  }
};
