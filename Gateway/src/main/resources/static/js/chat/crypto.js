const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

export class CryptoEngine {
  async generateUserKeyPair() {
    const subtle = this.#subtle();
    const keyPair = await subtle.generateKey(
      {
        name: 'RSA-OAEP',
        modulusLength: 2048,
        publicExponent: new Uint8Array([1, 0, 1]),
        hash: 'SHA-256'
      },
      true,
      ['encrypt', 'decrypt']
    );

    const publicKey = await subtle.exportKey('spki', keyPair.publicKey);
    const privateKey = await subtle.exportKey('pkcs8', keyPair.privateKey);

    return {
      publicKeyB64: this.bytesToBase64(new Uint8Array(publicKey)),
      privateKeyB64: this.bytesToBase64(new Uint8Array(privateKey))
    };
  }

  async encryptForPublicKey(dataB64, publicKeyB64) {
    const cryptoApi = this.#crypto();
    const subtle = this.#subtle();
    const publicKey = await this.importPublicKey(publicKeyB64);
    const aesRaw = cryptoApi.getRandomValues(new Uint8Array(32));
    const aesKey = await subtle.importKey(
      'raw',
      aesRaw,
      { name: 'AES-GCM' },
      false,
      ['encrypt']
    );
    const iv = cryptoApi.getRandomValues(new Uint8Array(12));
    const cipher = await subtle.encrypt(
      { name: 'AES-GCM', iv },
      aesKey,
      this.base64ToBytes(dataB64)
    );
    const encryptedKey = await subtle.encrypt(
      { name: 'RSA-OAEP' },
      publicKey,
      aesRaw
    );

    const payload = {
      alg: 'RSA-OAEP+AES-GCM',
      key: this.bytesToBase64(new Uint8Array(encryptedKey)),
      iv: this.bytesToBase64(iv),
      cipher: this.bytesToBase64(new Uint8Array(cipher))
    };

    return this.stringToBase64(JSON.stringify(payload));
  }

  async decryptWithPrivateKey(encryptedB64, privateKeyB64) {
    const subtle = this.#subtle();
    const privateKey = await this.importPrivateKey(privateKeyB64);
    const payload = this.tryParseEnvelope(encryptedB64);

    if (payload?.alg === 'RSA-OAEP+AES-GCM') {
      const aesRaw = await subtle.decrypt(
        { name: 'RSA-OAEP' },
        privateKey,
        this.base64ToBytes(payload.key)
      );
      const aesKey = await subtle.importKey(
        'raw',
        aesRaw,
        { name: 'AES-GCM' },
        false,
        ['decrypt']
      );
      const decrypted = await subtle.decrypt(
        { name: 'AES-GCM', iv: this.base64ToBytes(payload.iv) },
        aesKey,
        this.base64ToBytes(payload.cipher)
      );
      return this.bytesToBase64(new Uint8Array(decrypted));
    }

    const decrypted = await subtle.decrypt(
      { name: 'RSA-OAEP' },
      privateKey,
      this.base64ToBytes(encryptedB64)
    );
    return this.bytesToBase64(new Uint8Array(decrypted));
  }

  generateSenderKey() {
    const raw = this.#crypto().getRandomValues(new Uint8Array(32));
    return this.bytesToBase64(raw);
  }

  async encryptMessage(plainText, senderKeyB64) {
    const cryptoApi = this.#crypto();
    const subtle = this.#subtle();
    const key = await this.importSenderKey(senderKeyB64);
    const iv = cryptoApi.getRandomValues(new Uint8Array(12));
    const cipher = await subtle.encrypt(
      { name: 'AES-GCM', iv },
      key,
      textEncoder.encode(plainText)
    );

    const payload = {
      alg: 'AES-GCM',
      iv: this.bytesToBase64(iv),
      cipher: this.bytesToBase64(new Uint8Array(cipher))
    };

    return this.stringToBase64(JSON.stringify(payload));
  }

  async decryptMessage(messageB64, senderKeyB64) {
    const subtle = this.#subtle();
    const decoded = this.base64ToString(messageB64);
    const payload = JSON.parse(decoded);
    const key = await this.importSenderKey(senderKeyB64);
    const plain = await subtle.decrypt(
      { name: 'AES-GCM', iv: this.base64ToBytes(payload.iv) },
      key,
      this.base64ToBytes(payload.cipher)
    );
    return textDecoder.decode(plain);
  }

  async importPublicKey(publicKeyB64) {
    try {
      return await this.#subtle().importKey(
        'spki',
        this.base64ToBytes(publicKeyB64),
        { name: 'RSA-OAEP', hash: 'SHA-256' },
        true,
        ['encrypt']
      );
    } catch (error) {
      throw new Error(`Некорректный публичный ключ: ${error?.name || 'ImportError'}`);
    }
  }

  async importPrivateKey(privateKeyB64) {
    return this.#subtle().importKey(
      'pkcs8',
      this.base64ToBytes(privateKeyB64),
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      true,
      ['decrypt']
    );
  }

  async importSenderKey(senderKeyB64) {
    return this.#subtle().importKey(
      'raw',
      this.base64ToBytes(senderKeyB64),
      { name: 'AES-GCM' },
      false,
      ['encrypt', 'decrypt']
    );
  }

  base64ToBytes(base64) {
    const binary = atob(base64 || '');
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }

  bytesToBase64(bytes) {
    let binary = '';
    for (let i = 0; i < bytes.length; i += 1) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  stringToBase64(value) {
    return btoa(unescape(encodeURIComponent(value || '')));
  }

  base64ToString(value) {
    return decodeURIComponent(escape(atob(value || '')));
  }

  tryParseEnvelope(value) {
    try {
      const raw = this.base64ToString(value);
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') {
        return parsed;
      }
      return null;
    } catch {
      return null;
    }
  }

  #crypto() {
    const cryptoApi = globalThis.crypto;
    if (!cryptoApi || typeof cryptoApi.getRandomValues !== 'function') {
      throw new Error('Браузерная криптография недоступна. Откройте приложение в современном браузере.');
    }
    return cryptoApi;
  }

  #subtle() {
    const subtle = this.#crypto().subtle;
    if (!subtle) {
      throw new Error('Браузерная криптография недоступна для HTTP. Откройте приложение через HTTPS или localhost, иначе ключи и сообщения не смогут шифроваться.');
    }
    return subtle;
  }
}
