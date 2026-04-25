const DEFAULT_AVATAR = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 96 96'%3E%3Crect width='96' height='96' rx='28' fill='%23d7ebff'/%3E%3Cpath d='M20 36 34 18l10 20M76 36 62 18 52 38' fill='%2377aee9'/%3E%3Crect x='24' y='34' width='48' height='44' rx='22' fill='%23f7fbff' stroke='%236aa0d9' stroke-width='4'/%3E%3Ccircle cx='38' cy='52' r='4' fill='%2335577a'/%3E%3Ccircle cx='58' cy='52' r='4' fill='%2335577a'/%3E%3Cpath d='M42 63c4-3 8-3 12 0' stroke='%2335577a' stroke-width='4' stroke-linecap='round' fill='none'/%3E%3C/svg%3E";

export class SettingsUi {
  constructor() {
    this.nodes = {
      settingsBtn: document.getElementById('settingsBtn'),
      settingsModal: document.getElementById('settingsModal'),
      settingsCloseBtn: document.getElementById('settingsCloseBtn'),
      settingsTabs: Array.from(document.querySelectorAll('[data-settings-tab]')),
      settingsSections: Array.from(document.querySelectorAll('[data-settings-section]')),
      settingsTitle: document.getElementById('settingsTitle'),
      settingsMessage: document.getElementById('settingsMessage'),
      settingsUsername: document.getElementById('settingsUsername'),
      settingsAvatarFile: document.getElementById('settingsAvatarFile'),
      uploadAvatarBtn: document.getElementById('uploadAvatarBtn'),
      settingsAvatarPreview: document.getElementById('settingsAvatarPreview'),
      settingsCreatedAt: document.getElementById('settingsCreatedAt'),
      saveProfileBtn: document.getElementById('saveProfileBtn'),
      allowWritingCheckbox: document.getElementById('allowWritingCheckbox'),
      allowAddChatCheckbox: document.getElementById('allowAddChatCheckbox'),
      savePreferencesBtn: document.getElementById('savePreferencesBtn'),
      currentPassword: document.getElementById('currentPassword'),
      newPassword: document.getElementById('newPassword'),
      confirmPassword: document.getElementById('confirmPassword'),
      changePasswordBtn: document.getElementById('changePasswordBtn'),
      rotateKeysBtn: document.getElementById('rotateKeysBtn'),
      rotateSessionKeysBtn: document.getElementById('rotateSessionKeysBtn'),
      logoutAllBtn: document.getElementById('logoutAllBtn'),
      keyDiagnostics: document.getElementById('keyDiagnostics')
    };
  }

  bind(events) {
    this.nodes.settingsCloseBtn.addEventListener('click', events.onClose);
    this.nodes.settingsModal.addEventListener('click', (event) => {
      if (event.target === this.nodes.settingsModal) {
        events.onClose();
      }
    });
    this.nodes.settingsTabs.forEach((tab) => {
      tab.addEventListener('click', () => events.onTabChange(tab.dataset.settingsTab));
    });
    this.nodes.saveProfileBtn.addEventListener('click', events.onSaveProfile);
    this.nodes.uploadAvatarBtn.addEventListener('click', () => this.nodes.settingsAvatarFile.click());
    this.nodes.settingsAvatarFile.addEventListener('change', () => {
      const file = this.nodes.settingsAvatarFile.files?.[0] || null;
      this.nodes.settingsAvatarFile.value = '';
      if (file) {
        events.onUploadAvatar(file);
      }
    });
    this.nodes.settingsModal.addEventListener('paste', (event) => {
      const file = this.#imageFromClipboard(event);
      if (!file) {
        return;
      }
      event.preventDefault();
      events.onUploadAvatar(file);
    });
    this.nodes.savePreferencesBtn.addEventListener('click', events.onSavePreferences);
    this.nodes.changePasswordBtn.addEventListener('click', events.onChangePassword);
    this.nodes.rotateKeysBtn.addEventListener('click', events.onRotateKeys);
    this.nodes.rotateSessionKeysBtn.addEventListener('click', events.onRotateSessionKeys);
    this.nodes.logoutAllBtn.addEventListener('click', events.onLogoutAll);
  }

  open() {
    this.nodes.settingsModal.hidden = false;
    document.body.style.overflow = 'hidden';
  }

  close() {
    this.nodes.settingsModal.hidden = true;
    document.body.style.overflow = '';
  }

  setActiveTab(tabName) {
    this.nodes.settingsTabs.forEach((tab) => {
      tab.classList.toggle('active', tab.dataset.settingsTab === tabName);
    });
    this.nodes.settingsSections.forEach((section) => {
      section.hidden = section.dataset.settingsSection !== tabName;
    });
  }

  setStatus(text, type = 'info') {
    this.nodes.settingsMessage.textContent = text || '';
    this.nodes.settingsMessage.className = `settings-message ${type}`;
  }

  renderSettings(view) {
    this.nodes.settingsTitle.textContent = view?.username || '\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438';
    this.nodes.settingsUsername.value = view?.username || '';
    this.nodes.settingsCreatedAt.textContent = view?.createdAt
      ? `\u0410\u043a\u043a\u0430\u0443\u043d\u0442 \u0441\u043e\u0437\u0434\u0430\u043d: ${new Date(view.createdAt).toLocaleString()}`
      : '\u0410\u043a\u043a\u0430\u0443\u043d\u0442 \u0441\u043e\u0437\u0434\u0430\u043d: \u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e';
    this.nodes.allowWritingCheckbox.checked = Boolean(view?.allowWriting);
    this.nodes.allowAddChatCheckbox.checked = Boolean(view?.allowAddChat);
    this.previewAvatar(view?.avatarUrl || '');
  }

  previewAvatar(url) {
    this.nodes.settingsAvatarPreview.src = url || DEFAULT_AVATAR;
  }
  renderDiagnostics(diagnostics) {
    const lines = [
      `userId: ${diagnostics?.userId || '-'}`,
      `\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0439 public key: ${diagnostics?.currentPublicKeyId || '-'}`,
      `\u0421\u0435\u0440\u0432\u0435\u0440\u043d\u044b\u0439 public key: ${diagnostics?.serverPublicKeyId || '-'}`,
      `\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0445 user keys: ${diagnostics?.localUserKeyCount ?? 0}`,
      `\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0445 sender keys: ${diagnostics?.localSenderKeyCount ?? 0}`,
      `\u0421\u0438\u043d\u0445\u0440\u043e\u043d\u0438\u0437\u0430\u0446\u0438\u044f: ${diagnostics?.synchronized ? '\u0434\u0430' : '\u043d\u0435\u0442'}`,
      `pending private key: ${diagnostics?.hasPendingPrivateKey ? '\u0435\u0441\u0442\u044c' : '\u043d\u0435\u0442'}`,
      `pending public key id: ${diagnostics?.pendingPrivateKeyPublicId || '-'}`
    ];
    this.nodes.keyDiagnostics.textContent = lines.join('\n');
  }

  readProfileForm() {
    return {
      username: this.nodes.settingsUsername.value
    };
  }

  readPreferencesForm() {
    return {
      allowWriting: this.nodes.allowWritingCheckbox.checked,
      allowAddChat: this.nodes.allowAddChatCheckbox.checked
    };
  }

  readPasswordForm() {
    return {
      currentPassword: this.nodes.currentPassword.value,
      newPassword: this.nodes.newPassword.value,
      confirmPassword: this.nodes.confirmPassword.value
    };
  }

  clearPasswordForm() {
    this.nodes.currentPassword.value = '';
    this.nodes.newPassword.value = '';
    this.nodes.confirmPassword.value = '';
  }

  setAvatarUploadBusy(busy, progress = null) {
    this.nodes.uploadAvatarBtn.disabled = Boolean(busy);
    if (!busy) {
      this.nodes.uploadAvatarBtn.textContent = '\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u0430\u0432\u0430\u0442\u0430\u0440\u043a\u0443';
      return;
    }
    const suffix = Number.isFinite(progress) ? ` ${Math.max(0, Math.min(100, progress))}%` : '';
    this.nodes.uploadAvatarBtn.textContent = `\u0417\u0430\u0433\u0440\u0443\u0437\u043a\u0430${suffix}`;
  }

  #imageFromClipboard(event) {
    const items = Array.from(event.clipboardData?.items || []);
    for (const item of items) {
      if (!String(item.type || '').startsWith('image/')) {
        continue;
      }
      const blob = item.getAsFile();
      if (!blob) {
        continue;
      }
      const extension = item.type === 'image/png' ? 'png' : 'jpg';
      return new File([blob], `avatar-from-clipboard.${extension}`, { type: item.type });
    }
    return null;
  }

}
