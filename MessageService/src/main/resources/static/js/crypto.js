window.AppCrypto = (() => {
  const textEncoder = new TextEncoder();
  const textDecoder = new TextDecoder();

  const arrayBufferToBase64 = (buffer) => {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
    return btoa(binary);
  };

  const base64ToArrayBuffer = (base64) => {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  };

  async function generateUserKeyPair() {
    const keyPair = await crypto.subtle.generateKey(
      {
        name: 'RSA-OAEP',
        modulusLength: 2048,
        publicExponent: new Uint8Array([1, 0, 1]),
        hash: 'SHA-256'
      },
      true,
      ['encrypt', 'decrypt']
    );

    const publicKey = await crypto.subtle.exportKey('spki', keyPair.publicKey);
    const privateKey = await crypto.subtle.exportKey('pkcs8', keyPair.privateKey);

    return {
      algorithm: 'RSA-OAEP',
      publicKey: arrayBufferToBase64(publicKey),
      privateKey: arrayBufferToBase64(privateKey),
      createdAt: new Date().toISOString()
    };
  }

  function randomKeyName(prefix = 'chat-key') {
    return `${prefix}-${Date.now()}-${crypto.randomUUID()}`;
  }

  function randomBytes(length = 32) {
    const bytes = new Uint8Array(length);
    crypto.getRandomValues(bytes);
    return bytes;
  }

  async function generateChatKey() {
    return {
      encryptName: randomKeyName(),
      algorithm: 'AES-GCM',
      keyBase64: arrayBufferToBase64(randomBytes(32).buffer),
      createdAt: new Date().toISOString()
    };
  }

  async function encryptTextWithChatKey(plainText, chatKeyBase64) {
    const keyBytes = base64ToArrayBuffer(chatKeyBase64);
    const iv = randomBytes(12);
    const cryptoKey = await crypto.subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['encrypt']);
    const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, cryptoKey, textEncoder.encode(plainText));
    return JSON.stringify({
      iv: arrayBufferToBase64(iv.buffer),
      data: arrayBufferToBase64(encrypted)
    });
  }

  async function decryptTextWithChatKey(payload, chatKeyBase64) {
    const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload;
    const keyBytes = base64ToArrayBuffer(chatKeyBase64);
    const cryptoKey = await crypto.subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['decrypt']);
    const decrypted = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: new Uint8Array(base64ToArrayBuffer(parsed.iv)) },
      cryptoKey,
      base64ToArrayBuffer(parsed.data)
    );
    return textDecoder.decode(decrypted);
  }

  async function encryptChatKeyForPublicKey(chatKeyBase64, publicKeyBase64) {
    const publicKey = await crypto.subtle.importKey(
      'spki',
      base64ToArrayBuffer(publicKeyBase64),
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['encrypt']
    );

    const encrypted = await crypto.subtle.encrypt(
      { name: 'RSA-OAEP' },
      publicKey,
      textEncoder.encode(chatKeyBase64)
    );

    return arrayBufferToBase64(encrypted);
  }

  return {
    generateUserKeyPair,
    generateChatKey,
    encryptTextWithChatKey,
    decryptTextWithChatKey,
    encryptChatKeyForPublicKey,
    randomKeyName
  };
})();
