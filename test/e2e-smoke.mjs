import { readFile } from 'node:fs/promises';

const BASE = process.env.MESCAT_BASE_URL || 'http://localhost:8080';
const stamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14);
const users = [
  { username: `e2e_${stamp}_a`, password: 'Password123!' },
  { username: `e2e_${stamp}_b`, password: 'Password123!' },
  { username: `e2e_${stamp}_c`, password: 'Password123!' }
];

class Client {
  constructor(user) {
    this.user = user;
    this.cookie = '';
    this.id = null;
  }

  async request(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (this.cookie) headers.Cookie = this.cookie;
    const response = await fetch(`${BASE}${path}`, { ...options, headers, redirect: 'manual' });
    const setCookie = response.headers.getSetCookie ? response.headers.getSetCookie() : [];
    if (setCookie.length) {
      const next = new Map(this.cookie.split(';').map((part) => part.trim()).filter(Boolean).map((part) => {
        const i = part.indexOf('=');
        return [part.slice(0, i), part.slice(i + 1)];
      }));
      for (const raw of setCookie) {
        const first = raw.split(';')[0];
        const i = first.indexOf('=');
        if (i > 0) next.set(first.slice(0, i), first.slice(i + 1));
      }
      this.cookie = Array.from(next.entries()).map(([k, v]) => `${k}=${v}`).join('; ');
    }
    const text = await response.text();
    let body = text;
    try { body = text ? JSON.parse(text) : null; } catch {}
    if (!response.ok) {
      const err = new Error(`${options.method || 'GET'} ${path} -> ${response.status}: ${typeof body === 'string' ? body : JSON.stringify(body)}`);
      err.status = response.status;
      err.body = body;
      throw err;
    }
    return body;
  }

  postJson(path, body) {
    return this.request(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  }

  patchJson(path, body) {
    return this.request(path, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  }

  postText(path, text) {
    return this.request(path, { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: text });
  }

  get(path) {
    return this.request(path);
  }
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function b64(text) {
  return Buffer.from(text, 'utf8').toString('base64');
}

async function uploadPostForm(upload, bytes, fileName, type) {
  const form = new FormData();
  for (const [key, value] of Object.entries(upload.formFields || {})) {
    form.append(key, value);
  }
  form.append('file', new Blob([bytes], { type }), fileName);
  const response = await fetch(upload.uploadUrl, { method: upload.method || 'POST', body: form });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`S3 POST ${upload.uploadUrl} -> ${response.status}: ${text.slice(0, 500)}`);
  }
}

async function main() {
  console.log(`BASE=${BASE}`);
  const clients = users.map((user) => new Client(user));

  for (const client of clients) {
    await client.postJson('/auth/reg', client.user);
    client.id = await client.get('/api/getId');
    assert(/^[0-9a-f-]{36}$/i.test(String(client.id)), `bad user id for ${client.user.username}`);
    console.log(`registered ${client.user.username} -> ${client.id}`);
  }

  for (const client of clients) {
    const settings = await client.get('/api/settings');
    assert(settings.username === client.user.username, `settings username mismatch for ${client.user.username}`);
    await client.postText('/api/encrypt_key/new_key', b64(`public-key-${client.user.username}`));
    const publicKey = await client.get('/api/encrypt_key/');
    assert(publicKey?.id && publicKey?.key, `public key not saved for ${client.user.username}`);
    client.publicKey = publicKey;
  }

  const search = await clients[0].get(`/api/search_by_username/${encodeURIComponent(users[1].username.slice(0, -1))}`);
  assert(Array.isArray(search) && search.some((item) => item.username === users[1].username), 'search_by_username did not find user B');

  const personal = await clients[0].postJson('/api/personal_chat', { userId: clients[1].id });
  assert(personal?.chatId, 'personal chat was not created');
  const personalChatId = personal.chatId;
  console.log(`personal chat=${personalChatId}`);

  const group = await clients[0].postJson('/api/group_chat', { title: `e2e group ${stamp}`, avatarUrl: '' });
  assert(group?.chatId, 'group chat was not created');
  const groupChatId = group.chatId;
  await clients[0].postJson('/api/add_user_in_chat', { chatId: groupChatId, userTarget: clients[1].id });
  await clients[0].postJson('/api/add_user_in_chat', { chatId: groupChatId, userTarget: clients[2].id });
  const members = await clients[1].get(`/api/chats/${groupChatId}/members`);
  assert(Array.isArray(members) && members.length >= 3, 'group members are not visible for user B');
  console.log(`group chat=${groupChatId}, members=${members.length}`);

  const message = await clients[0].postJson('/api/sendMessage', {
    chatId: personalChatId,
    message: b64(JSON.stringify({ alg: 'TEST', cipher: 'hello from e2e' })),
    encryptionName: crypto.randomUUID()
  });
  assert(message?.messageId, 'message was not sent');
  const messagesForB = await clients[1].get(`/api/messages/${personalChatId}?limit=20`);
  assert(Array.isArray(messagesForB) && messagesForB.some((item) => Number(item.messageId) === Number(message.messageId)), 'sent message is not visible for user B');
  console.log(`message=${message.messageId}`);

  const keySend = await clients[0].postJson('/api/encrypt_message_key/send', {
    chatId: personalChatId,
    requestEncryptMessageKeyForUsers: [{ userTarget: clients[1].id, key: b64('sender-key-for-b'), publicKeyUser: clients[1].publicKey.id }]
  });
  assert(keySend?.encryptName, 'send message key did not return encryptName');
  const pendingKeys = await clients[1].get('/api/encrypt_message_key/pending');
  assert(Array.isArray(pendingKeys) && pendingKeys.length > 0, 'pending message key not delivered to user B');
  await clients[1].postJson('/api/encrypt_message_key/delete', { keyId: pendingKeys[0].id || pendingKeys[0].keyId });
  console.log(`message key encryptName=${keySend.encryptName}`);

  const tinyPng = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=', 'base64');

  const fileUpload = await clients[0].postJson('/api/files/upload-url', {
    chatId: groupChatId,
    originalFileName: 'hello-e2e.txt',
    mimeType: 'text/plain',
    sizeBytes: 18
  });
  assert(fileUpload?.fileId && fileUpload?.uploadUrl && fileUpload?.formFields, 'file upload-url is invalid');
  await uploadPostForm(fileUpload, Buffer.from('hello file from e2e', 'utf8'), 'hello-e2e.txt', 'text/plain');
  const fileReady = await clients[0].postJson(`/api/files/${fileUpload.fileId}/complete`, {});
  assert(fileReady?.fileId === fileUpload.fileId && fileReady?.status === 'READY', 'file did not become READY');
  const groupFilesForB = await clients[1].get(`/api/files/chats/${groupChatId}`);
  assert(Array.isArray(groupFilesForB) && groupFilesForB.some((item) => item.fileId === fileUpload.fileId), 'ready file is not visible for group member');
  const download = await clients[1].get(`/api/files/${fileUpload.fileId}/download-url`);
  assert(download?.downloadUrl, 'download url not returned');
  console.log(`file=${fileUpload.fileId}`);

  const imageUpload = await clients[0].postJson('/api/files/upload-url', {
    chatId: groupChatId,
    originalFileName: 'photo-e2e.png',
    mimeType: 'image/png',
    sizeBytes: tinyPng.length
  });
  assert(imageUpload?.fileId && imageUpload?.uploadUrl && imageUpload?.formFields, 'image upload-url is invalid');
  await uploadPostForm(imageUpload, tinyPng, 'photo-e2e.png', 'image/png');
  const imageReady = await clients[0].postJson(`/api/files/${imageUpload.fileId}/complete`, {});
  assert(imageReady?.fileType === 'IMAGE', 'image file type was not detected');
  const imagePreview = await clients[1].get(`/api/files/${imageUpload.fileId}/download-url?inline=true`);
  assert(imagePreview?.downloadUrl, 'inline image preview url not returned');
  console.log(`image=${imageUpload.fileId}`);

  const tinyVideo = await readFile(new URL('./assets/tiny.mp4', import.meta.url));
  const videoUpload = await clients[0].postJson('/api/files/upload-url', {
    chatId: groupChatId,
    originalFileName: 'video-e2e.mp4',
    mimeType: 'video/mp4',
    sizeBytes: tinyVideo.length
  });
  assert(videoUpload?.fileId && videoUpload?.uploadUrl && videoUpload?.formFields, 'video upload-url is invalid');
  await uploadPostForm(videoUpload, tinyVideo, 'video-e2e.mp4', 'video/mp4');
  const videoReady = await clients[0].postJson(`/api/files/${videoUpload.fileId}/complete`, {});
  assert(videoReady?.fileType === 'VIDEO', 'video file type was not detected');
  const videoPreview = await clients[1].get(`/api/files/${videoUpload.fileId}/download-url?inline=true`);
  assert(videoPreview?.downloadUrl, 'inline video preview url not returned');
  console.log(`video=${videoUpload.fileId}`);

  const avatarUpload = await clients[0].postJson('/api/settings/profile/avatar/upload-url', {
    originalFileName: 'avatar-e2e.png',
    mimeType: 'image/png',
    sizeBytes: tinyPng.length
  });
  assert(avatarUpload?.uploadId && avatarUpload?.uploadUrl && avatarUpload?.formFields, 'avatar upload-url is invalid');
  await uploadPostForm(avatarUpload, tinyPng, 'avatar-e2e.png', 'image/png');
  const avatarReady = await clients[0].postJson(`/api/settings/profile/avatar/${avatarUpload.uploadId}/complete`, {});
  assert(avatarReady?.avatarUrl, 'avatar upload did not return public avatarUrl');
  const settingsAfterAvatar = await clients[0].get('/api/settings');
  assert(settingsAfterAvatar.avatarUrl === avatarReady.avatarUrl, 'uploaded avatar url was not saved in profile');
  const avatarPublicResponse = await fetch(avatarReady.avatarUrl);
  assert(avatarPublicResponse.ok, `uploaded avatar is not public: ${avatarPublicResponse.status}`);
  console.log(`avatarUrl=${avatarReady.avatarUrl}`);

  await clients[0].postJson('/api/delete', { chatId: personalChatId, messageId: message.messageId });
  const messagesAfterDelete = await clients[1].get(`/api/messages/${personalChatId}?limit=20`);
  assert(!messagesAfterDelete.some((item) => Number(item.messageId) === Number(message.messageId)), 'deleted message is still visible');

  console.log('E2E_SMOKE_OK');
}

main().catch((error) => {
  console.error('E2E_SMOKE_FAILED');
  console.error(error.stack || error.message || error);
  process.exit(1);
});

