import { ApiClient } from './api.js';
import { ChatManager } from './chat-manager.js';
import { CryptoEngine } from './crypto.js';
import { FrontendDb } from './db.js';
import { FileUploader } from './file-uploader.js';
import { KeyManager } from './key-manager.js';
import { SettingsManager } from './settings-manager.js';
import { SettingsUi } from './settings-ui.js';
import { ChatUi } from './ui.js';
import { WsManager } from './ws.js';

const api = new ApiClient();
const db = new FrontendDb();
const crypto = new CryptoEngine();
const ui = new ChatUi();
const settingsUi = new SettingsUi();
const keyManager = new KeyManager({ api, db, crypto, ui });
const fileUploader = new FileUploader({ api });
const chatManager = new ChatManager({ api, ui, keyManager, fileUploader });
const settingsManager = new SettingsManager({ api, ui, settingsUi, keyManager, fileUploader });

let userId = null;
let ws = null;
const realtimeState = {
  payloads: [],
  timer: null,
  running: false
};
const memberSearchState = {
  timer: null,
  requestId: 0
};

void start();

async function start() {
  try {
    await db.init();
    userId = await api.getCurrentUserId();
    userId = String(userId || '').replace(/^"|"$/g, '');
    if (!userId) {
      throw new Error('Не удалось определить текущую сессию. Войдите еще раз.');
    }

    ui.setUserId(userId);
    ui.renderActiveChat(null);
    chatManager.init(userId);
    await keyManager.init(userId);
    await settingsManager.init(userId);
    bindUi();
    await stepBootstrapKeys();
    await settingsManager.refreshDiagnostics();
    await stepChats();
    stepWebSocket();
    ui.appendStatus('Интерфейс готов. Можно искать чаты и общаться.', 'ok');
  } catch (error) {
    ui.appendStatus(error.message || 'Не удалось инициализировать приложение.', 'error');
  }
}

function bindUi() {
  ui.bind({
    onOpenSettings: async (tabName) => {
      await runAction(async () => {
        await settingsManager.open(tabName);
      });
    },
    onExitSearch: () => {
      ui.setSearchMode(false);
      ui.clearSearchResults();
    },
    onLogout: async () => {
      await runAction(async () => {
        try {
          await api.logout();
        } finally {
          if (ws) {
            ws.disconnect();
          }
          window.location.href = '/auth/login';
        }
      });
    },
    onReload: () => window.location.reload(),
    onBootstrapKeys: async () => {
      await runAction(async () => {
        await stepBootstrapKeys();
        ui.appendStatus('Ключи синхронизированы повторно.', 'ok');
      });
    },
    onSearchUser: async () => {
      await runAction(async () => {
        const query = ui.nodes.searchInput.value;
        const results = await chatManager.searchDialogs(query);
        ui.renderSearchResults(results, async (result) => {
          await runAction(async () => {
            await chatManager.openSearchResult(result);
            ui.setSearchMode(false);
            ui.clearSearchResults();
          });
        });
      });
    },
    onSendMessage: async () => {
      await runAction(async () => {
        const text = ui.nodes.messageInput.value;
        const imported = await keyManager.ingestPendingMessageKeys();
        if (imported.importedCount > 0 && chatManager.activeChatId) {
          await chatManager.refreshVisibleMessages(chatManager.activeChatId);
        }
        await keyManager.retryFailedSenderKeyDeliveries();
        await chatManager.sendMessage(text);
        ui.nodes.messageInput.value = '';
        ui.appendStatus('Сообщение отправлено.', 'ok');
      });
    },
    onAttachFile: async (file) => {
      await runAction(async () => {
        await chatManager.sendFile(file);
      });
    },
    onOpenFile: async (fileId) => {
      await runAction(async () => {
        await chatManager.openFile(fileId);
      });
    },
    onCreateGroupChat: async () => {
      await runAction(async () => {
        const title = ui.nodes.groupTitleInput.value;
        await chatManager.createGroupChat(title);
        ui.nodes.groupTitleInput.value = '';
        settingsUi.setStatus('Групповой чат создан.', 'ok');
        ui.appendStatus('Групповой чат создан.', 'ok');
      });
    },
    onCreateGroupChatModal: async () => {
      await runAction(async () => {
        const title = ui.nodes.createChatTitleInput.value;
        await chatManager.createGroupChat(title);
        ui.nodes.createChatTitleInput.value = '';
        ui.closeCreateChatModal();
        ui.appendStatus('Групповой чат создан.', 'ok');
      });
    },
    onWsReconnect: () => {
      void runAction(async () => {
        if (ws) {
          ws.connect(chatManager.chats.map((chat) => chat.chatId));
        }
        ui.appendStatus('Переподключение websocket запущено.', 'info');
      });
    },
    onMemberAction: async (action) => {
      await runAction(async () => {
        const username = ui.nodes.memberInput.value;
        const selectedUserId = ui.getSelectedMemberUserId?.();
        await chatManager.memberAction(action, username, selectedUserId);
        ui.clearMemberSelection?.();
        ui.appendStatus(`Действие с участником выполнено: ${action}.`, 'ok');
      });
    },
    onMemberSearch: (query) => {
      scheduleMemberSearch(query);
    },
    onOpenUserProfile: async (profileUserId, anchor) => {
      await runAction(async () => {
        const profile = await api.getUserProfile(profileUserId);
        ui.renderUserProfilePopover(profile, anchor, async (selectedProfile) => {
          await runAction(async () => {
            await chatManager.openPersonalChatByUserId(selectedProfile.id || selectedProfile.userId);
          });
        });
      });
    },
    onToggleChatMenu: () => {
      const chat = chatManager.activeChat;
      if (!chat) {
        return;
      }

      if (!ui.nodes.chatMenu.hidden) {
        ui.hideChatMenu();
        return;
      }

      const items = [
        {
          label: 'Открыть участников',
          onClick: () => {
            ui.setOptionsPanelOpen(true);
            ui.renderMembers(chat.participants || [], chat);
          }
        }
      ];

      if (chat.chatType === 'PERSONAL') {
        items.push({
          label: 'Заблокировать пользователя',
          danger: true,
          onClick: () => {
            void runAction(async () => {
              await chatManager.memberAction('block', '');
              ui.appendStatus('Пользователь заблокирован в этом диалоге.', 'ok');
            });
          }
        });
      } else {
        if (chat.canManageMembers) {
          items.push(
            {
              label: 'Добавить участника',
              onClick: () => {
                ui.setOptionsPanelOpen(true);
                ui.nodes.memberInput.placeholder = 'Username для добавления';
                ui.nodes.memberInput.focus();
              }
            },
            {
              label: 'Удалить участника',
              onClick: () => {
                ui.setOptionsPanelOpen(true);
                ui.nodes.memberInput.placeholder = 'Username для удаления';
                ui.nodes.memberInput.focus();
              }
            },
            {
              label: 'Заблокировать участника',
              onClick: () => {
                ui.setOptionsPanelOpen(true);
                ui.nodes.memberInput.placeholder = 'Username для блокировки';
                ui.nodes.memberInput.focus();
              }
            }
          );
        }

        if (chat.canDeleteChat) {
          items.push({
            label: 'Удалить группу',
            danger: true,
            onClick: () => {
              void runAction(async () => {
                await chatManager.deleteActiveChat();
                ui.appendStatus('Группа удалена.', 'ok');
              });
            }
          });
        }
      }

      ui.renderChatMenu(items);
    },
    onMessageContext: ({ x, y, payload }) => {
      const visibleMessages = chatManager.messagesByChat.get(String(chatManager.activeChatId)) || [];
      const message = visibleMessages.find((item) => Number(item.messageId) === Number(payload.messageId));
      if (!chatManager.canDeleteMessage(message)) {
        ui.hideMessageMenu();
        return;
      }

      ui.renderMessageMenu(x, y, [{
        label: 'Удалить сообщение',
        danger: true,
        onClick: () => {
          void runAction(async () => {
            await chatManager.deleteMessage(payload.messageId);
            ui.appendStatus('Сообщение удалено.', 'ok');
          });
        }
      }], payload);
    }
  });

  settingsUi.bind({
    onOpen: async () => { await runAction(async () => { await settingsManager.open(); }); },
    onClose: () => settingsManager.close(),
    onTabChange: (tabName) => settingsManager.setTab(tabName),
    onSaveProfile: async () => { await runAction(async () => { await settingsManager.saveProfile(); }); },
    onUploadAvatar: async (file) => { await runAction(async () => { await settingsManager.uploadAvatar(file); }); },
    onSavePreferences: async () => { await runAction(async () => { await settingsManager.savePreferences(); }); },
    onChangePassword: async () => { await runAction(async () => { await settingsManager.changePassword(); }); },
    onRotateKeys: async () => { await runAction(async () => { await settingsManager.rotateKeys(); }); },
    onRotateSessionKeys: async () => { await runAction(async () => { await settingsManager.rotateSessionKeys(); }); },
    onLogoutAll: async () => { await runAction(async () => { await settingsManager.logoutAll(); }); }
  });
}

async function stepBootstrapKeys() {
  ui.appendStatus('Проверяю и синхронизирую ключи пользователя...', 'info');
  await keyManager.bootstrap();
  const imported = await keyManager.ingestPendingMessageKeys();
  if (imported.importedCount > 0 && chatManager.activeChatId) {
    await chatManager.refreshVisibleMessages(chatManager.activeChatId);
  }
  await keyManager.retryFailedSenderKeyDeliveries();
  await settingsManager.refreshDiagnostics();
}

async function stepChats() {
  ui.appendStatus('Загружаю список чатов...', 'info');
  await chatManager.refreshChats();
}
function stepWebSocket() {
  ui.appendStatus('Запускаю realtime-синхронизацию...', 'info');
  ws = new WsManager({
    ui,
    onMessage: (rawBody) => {
      try {
        if (!rawBody) {
          return;
        }
        const payload = JSON.parse(rawBody);
        scheduleRealtimeSync(payload);
      } catch (error) {
        ui.appendStatus(`Не удалось обработать realtime-событие: ${error.message}`, 'error');
      }
    }
  });
  chatManager.setWebSocket(ws);
  ws.connect(chatManager.chats.map((chat) => chat.chatId));
}

function scheduleRealtimeSync(payload) {
  realtimeState.payloads.push(payload);
  if (realtimeState.timer) {
    clearTimeout(realtimeState.timer);
  }
  realtimeState.timer = setTimeout(() => {
    realtimeState.timer = null;
    void processRealtimeBatch();
  }, 180);
}

async function processRealtimeBatch() {
  if (realtimeState.running) {
    return;
  }

  realtimeState.running = true;
  try {
    const payloads = realtimeState.payloads.splice(0);
    if (payloads.length === 0) {
      return;
    }

    const userKeysSynced = await syncUserPrivateKeysIfNeeded(payloads);
    const imported = await keyManager.ingestPendingMessageKeys();
    for (const payload of payloads) {
      await chatManager.handleRealtimeEvent(payload, ws);
    }
    await keyManager.retryFailedSenderKeyDeliveries();

    const activeChatId = chatManager.activeChatId;
    const touchedActiveChat = activeChatId && payloads.some((payload) => {
      const chatId = getRealtimeChatId(payload);
      return (!chatId || Number(chatId) === Number(activeChatId)) && !isOwnRealtimePayload(payload);
    });

    if (imported.importedCount > 0 && activeChatId) {
      await chatManager.refreshVisibleMessages(activeChatId, { preserveScroll: true });
    }
    if (userKeysSynced) {
      ui.appendStatus('Новый пользовательский ключ получен и сохранён локально.', 'ok');
    }

    if (touchedActiveChat) {
      await chatManager.loadMessages(activeChatId, { preserveScroll: true });
    }

    await chatManager.refreshChats();
    syncWsChats();
  } catch (error) {
    ui.appendStatus(`Не удалось обработать realtime-синхронизацию: ${error.message}`, 'error');
  } finally {
    realtimeState.running = false;
    if (realtimeState.payloads.length > 0) {
      void processRealtimeBatch();
    }
  }
}

async function syncUserPrivateKeysIfNeeded(payloads) {
  const shouldSync = payloads.some((payload) => {
    const type = String(payload?.type || '').toUpperCase();
    return type === 'NEW_PRIVATE_KEY'
      || type === 'NEW_PUBLIC_KEY'
      || Boolean(payload?.payload?.newPrivateKey);
  });

  if (!shouldSync) {
    return false;
  }

  const result = await keyManager.syncUserPrivateKeysFromServer();
  return Boolean(result?.imported);
}

function getRealtimeChatId(payload) {
  const body = payload?.payload;
  return body?.message?.chat?.chatId
    || body?.message?.chatId
    || body?.file?.chat?.chatId
    || body?.file?.chatId
    || body?.chat?.chatId
    || body?.chatId
    || body?.chatUserEntity?.chat?.chatId
    || body?.usersBlackListEntity?.chat?.chatId
    || null;
}

function getRealtimeSenderId(payload) {
  const body = payload?.payload;
  return body?.message?.senderId
    || body?.file?.senderId
    || body?.senderId
    || payload?.senderId
    || null;
}

function isOwnRealtimePayload(payload) {
  const senderId = getRealtimeSenderId(payload);
  return senderId && String(senderId) === String(userId);
}

function syncWsChats() {
  if (ws) {
    ws.syncChats(chatManager.chats.map((chat) => chat.chatId));
  }
}

function scheduleMemberSearch(query) {
  if (memberSearchState.timer) {
    clearTimeout(memberSearchState.timer);
  }

  memberSearchState.timer = setTimeout(() => {
    memberSearchState.timer = null;
    void runMemberSearch(query);
  }, 220);
}

async function runMemberSearch(query) {
  const requestId = ++memberSearchState.requestId;
  const chat = chatManager.activeChat;
  const clean = String(query || '').trim();

  if (!chat || chat.chatType !== 'GROUP' || !chat.canManageMembers || clean.length < 2) {
    ui.clearMemberSuggestions?.();
    return;
  }

  try {
    const users = await chatManager.searchUsersOnly(clean);
    if (requestId !== memberSearchState.requestId) {
      return;
    }
    ui.renderMemberSuggestions(users);
  } catch (error) {
    if (requestId === memberSearchState.requestId) {
      ui.clearMemberSuggestions?.();
    }
  }
}

async function runAction(action) {
  try {
    await action();
  } catch (error) {
    const message = error?.message || 'Ошибка операции.';
    ui.appendStatus(message, 'error');
    settingsUi.setStatus(message, 'error');
  }
}

