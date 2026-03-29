window.AppStorage = (() => {
  const cfg = window.APP_CONFIG.storageKeys;

  const getJson = (key, fallback) => {
    try {
      const value = localStorage.getItem(key);
      return value ? JSON.parse(value) : fallback;
    } catch {
      return fallback;
    }
  };

  const setJson = (key, value) => localStorage.setItem(key, JSON.stringify(value));

  return {
    getUserId: () => localStorage.getItem(cfg.userId),
    setUserId: (userId) => localStorage.setItem(cfg.userId, userId),

    getUserKeys: () => getJson(cfg.userKeys, null),
    setUserKeys: (keys) => setJson(cfg.userKeys, keys),

    getChatKeys: () => getJson(cfg.chatKeys, {}),
    getChatKey: (chatId) => getJson(cfg.chatKeys, {})[chatId] || null,
    setChatKey: (chatId, value) => {
      const map = getJson(cfg.chatKeys, {});
      map[chatId] = value;
      setJson(cfg.chatKeys, map);
    },

    getCounters: () => getJson(cfg.counters, {}),
    getCounter: (chatId) => getJson(cfg.counters, {})[chatId] || 0,
    setCounter: (chatId, count) => {
      const counters = getJson(cfg.counters, {});
      counters[chatId] = count;
      setJson(cfg.counters, counters);
    },
    incCounter: (chatId) => {
      const counters = getJson(cfg.counters, {});
      counters[chatId] = (counters[chatId] || 0) + 1;
      setJson(cfg.counters, counters);
      return counters[chatId];
    }
  };
})();
