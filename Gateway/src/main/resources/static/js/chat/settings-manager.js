import { LIMITS } from './constants.js';

export class SettingsManager {
  constructor({ api, ui, settingsUi, keyManager, fileUploader }) {
    this.api = api;
    this.ui = ui;
    this.settingsUi = settingsUi;
    this.keyManager = keyManager;
    this.fileUploader = fileUploader;
    this.userId = null;
    this.currentSettings = null;
    this.activeTab = 'profile';
  }

  async init(userId) {
    this.userId = userId;
    this.settingsUi.setActiveTab(this.activeTab);
  }

  async open(tabName = null) {
    if (tabName) {
      this.setTab(tabName);
    }
    this.settingsUi.open();
    await this.refresh();
  }

  close() {
    this.settingsUi.close();
  }

  setTab(tabName) {
    this.activeTab = tabName || 'profile';
    this.settingsUi.setActiveTab(this.activeTab);
  }

  async refresh() {
    const settings = await this.api.getSettings();
    this.currentSettings = settings;
    this.settingsUi.renderSettings(settings);
    await this.refreshDiagnostics();
    this.settingsUi.setStatus('\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u044b.', 'ok');
  }

  async saveProfile() {
    const form = this.settingsUi.readProfileForm();
    const currentUsername = this.currentSettings?.username || '';

    let updated = false;

    if (form.username.trim() !== currentUsername) {
      this.currentSettings = await this.api.updateProfileUsername(form.username.trim());
      updated = true;
    }

    if (!updated) {
      this.settingsUi.setStatus('\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0439 \u043f\u0440\u043e\u0444\u0438\u043b\u044f \u043d\u0435\u0442.', 'info');
      return;
    }

    this.settingsUi.renderSettings(this.currentSettings);
    this.settingsUi.setStatus('\u041f\u0440\u043e\u0444\u0438\u043b\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d.', 'ok');
    this.ui.appendStatus('\u041f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044c\u0441\u043a\u0438\u0435 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u043f\u0440\u043e\u0444\u0438\u043b\u044f \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u044b.', 'ok');
  }

  async uploadAvatar(file) {
    this.#validateAvatar(file);
    this.settingsUi.setAvatarUploadBusy(true, 0);
    try {
      const result = await this.fileUploader.uploadAvatar(file, (progress) => {
        this.settingsUi.setAvatarUploadBusy(true, progress);
      });
      if (result?.avatarUrl) {
        this.currentSettings = {
          ...(this.currentSettings || {}),
          avatarUrl: result.avatarUrl
        };
        this.settingsUi.renderSettings(this.currentSettings);
      } else {
        await this.refresh();
      }
      this.settingsUi.setStatus('\u0410\u0432\u0430\u0442\u0430\u0440\u043a\u0430 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u0430.', 'ok');
      this.ui.appendStatus('\u0410\u0432\u0430\u0442\u0430\u0440\u043a\u0430 \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0430. \u041e\u0441\u0442\u0430\u043b\u044c\u043d\u044b\u0435 \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u0438 \u0443\u0432\u0438\u0434\u044f\u0442 \u0435\u0435 \u043a\u0430\u043a \u043e\u0431\u044b\u0447\u043d\u0443\u044e \u043a\u0430\u0440\u0442\u0438\u043d\u043a\u0443 \u043f\u043e \u0441\u0441\u044b\u043b\u043a\u0435.', 'ok');
    } finally {
      this.settingsUi.setAvatarUploadBusy(false);
    }
  }

  async savePreferences() {
    const form = this.settingsUi.readPreferencesForm();
    let settings = await this.api.updateAllowWriting(form.allowWriting);
    settings = await this.api.updateAllowAddChat(form.allowAddChat);
    this.currentSettings = settings;
    this.settingsUi.renderSettings(settings);
    this.settingsUi.setStatus('\u041e\u0433\u0440\u0430\u043d\u0438\u0447\u0435\u043d\u0438\u044f \u0438 \u043f\u043e\u0432\u0435\u0434\u0435\u043d\u0438\u0435 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u044b.', 'ok');
    this.ui.appendStatus('\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u043e\u0433\u0440\u0430\u043d\u0438\u0447\u0435\u043d\u0438\u0439 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u044b.', 'ok');
  }

  async changePassword() {
    const dto = this.settingsUi.readPasswordForm();
    const result = await this.api.changePassword(dto);
    this.settingsUi.clearPasswordForm();
    this.settingsUi.setStatus(typeof result === 'string' ? result : '\u041f\u0430\u0440\u043e\u043b\u044c \u0438\u0437\u043c\u0435\u043d\u0435\u043d.', 'ok');
    this.ui.appendStatus('\u041f\u0430\u0440\u043e\u043b\u044c \u0438\u0437\u043c\u0435\u043d\u0435\u043d. \u0422\u0435\u043a\u0443\u0449\u0430\u044f \u0441\u0435\u0441\u0441\u0438\u044f \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430.', 'ok');
    window.location.href = '/auth/login';
  }

  async logoutAll() {
    const result = await this.api.logoutAllSessions();
    this.settingsUi.setStatus(typeof result === 'string' ? result : '\u0412\u0441\u0435 \u0441\u0435\u0441\u0441\u0438\u0438 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u044b.', 'ok');
    this.ui.appendStatus('\u0412\u0441\u0435 \u0441\u0435\u0441\u0441\u0438\u0438 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u044b.', 'ok');
    window.location.href = '/auth/login';
  }

  async rotateKeys() {
    await this.keyManager.rotateUserKeyPair();
    await this.refreshDiagnostics();
    this.settingsUi.setStatus('\u041f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044c\u0441\u043a\u0430\u044f \u043f\u0430\u0440\u0430 \u043a\u043b\u044e\u0447\u0435\u0439 \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0430.', 'ok');
    this.ui.appendStatus('\u041f\u0430\u0440\u0430 \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044c\u0441\u043a\u0438\u0445 \u043a\u043b\u044e\u0447\u0435\u0439 \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0430.', 'ok');
  }

  async rotateSessionKeys() {
    await this.keyManager.createSessionUserKeyPair();
    await this.refreshDiagnostics();
    this.settingsUi.setStatus('\u0421\u043e\u0437\u0434\u0430\u043d\u0430 \u043d\u043e\u0432\u0430\u044f \u043f\u0430\u0440\u0430 \u043a\u043b\u044e\u0447\u0435\u0439 \u0442\u043e\u043b\u044c\u043a\u043e \u0434\u043b\u044f \u0442\u0435\u043a\u0443\u0449\u0435\u0439 \u0441\u0435\u0441\u0441\u0438\u0438.', 'ok');
    this.ui.appendStatus('\u0421\u043e\u0437\u0434\u0430\u043d\u0430 \u043d\u043e\u0432\u0430\u044f \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044c\u0441\u043a\u0430\u044f \u043f\u0430\u0440\u0430 \u043a\u043b\u044e\u0447\u0435\u0439 \u0434\u043b\u044f \u0442\u0435\u043a\u0443\u0449\u0435\u0439 \u0441\u0435\u0441\u0441\u0438\u0438.', 'ok');
  }

  async refreshDiagnostics() {
    const diagnostics = await this.keyManager.getDiagnostics();
    this.settingsUi.renderDiagnostics(diagnostics);
    return diagnostics;
  }

  #validateAvatar(file) {
    if (!file) {
      throw new Error('\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0438\u0437\u043e\u0431\u0440\u0430\u0436\u0435\u043d\u0438\u0435 \u0434\u043b\u044f \u0430\u0432\u0430\u0442\u0430\u0440\u043a\u0438.');
    }
    if (!String(file.type || '').startsWith('image/')) {
      throw new Error('\u0410\u0432\u0430\u0442\u0430\u0440\u043a\u043e\u0439 \u043c\u043e\u0436\u0435\u0442 \u0431\u044b\u0442\u044c \u0442\u043e\u043b\u044c\u043a\u043e \u0438\u0437\u043e\u0431\u0440\u0430\u0436\u0435\u043d\u0438\u0435.');
    }
    if (Number(file.size || 0) > LIMITS.avatarMaxBytes) {
      throw new Error('\u0410\u0432\u0430\u0442\u0430\u0440\u043a\u0430 \u0434\u043e\u043b\u0436\u043d\u0430 \u0431\u044b\u0442\u044c \u043d\u0435 \u0431\u043e\u043b\u044c\u0448\u0435 5 \u041c\u0411.');
    }
  }
}
