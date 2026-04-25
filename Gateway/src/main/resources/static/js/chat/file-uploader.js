export class FileUploader {
  constructor({ api }) {
    this.api = api;
  }

  async uploadChatFile(chatId, file, onProgress = null) {
    const upload = await this.api.createFileUploadUrl(chatId, file);
    await this.#uploadPostForm(upload, file, onProgress);
    return this.api.completeFileUpload(upload.fileId);
  }

  async uploadAvatar(file, onProgress = null) {
    const upload = await this.api.createAvatarUploadUrl(file);
    await this.#uploadPostForm(upload, file, onProgress);
    return this.api.completeAvatarUpload(upload.uploadId);
  }

  #uploadPostForm(upload, file, onProgress) {
    if (!upload?.uploadUrl || !upload?.formFields || !file) {
      return Promise.reject(new Error('Не удалось подготовить загрузку файла.'));
    }

    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      const form = new FormData();

      Object.entries(upload.formFields).forEach(([key, value]) => {
        form.append(key, value);
      });
      form.append('file', file);

      xhr.open(upload.method || 'POST', upload.uploadUrl, true);
      xhr.upload.onprogress = (event) => {
        if (!event.lengthComputable || typeof onProgress !== 'function') {
          return;
        }
        onProgress(Math.round((event.loaded / event.total) * 100));
      };
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 400) {
          onProgress?.(100);
          resolve();
          return;
        }
        reject(new Error(`S3 отклонил загрузку файла (${xhr.status}).`));
      };
      xhr.onerror = () => reject(new Error('Не удалось загрузить файл в хранилище.'));
      xhr.ontimeout = () => reject(new Error('Время загрузки файла истекло.'));
      xhr.timeout = 5 * 60 * 1000;
      xhr.send(form);
    });
  }
}
