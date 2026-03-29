window.AppWs = (() => {
  const cfg = window.APP_CONFIG.ws;
  let socket = null;
  let connected = false;

  function connect({ onOpen, onClose, onError, onMessage } = {}) {
    if (!cfg.enabled) return null;
    try {
      socket = new WebSocket(cfg.url);
      socket.addEventListener('open', (event) => {
        connected = true;
        onOpen?.(event);
      });
      socket.addEventListener('close', (event) => {
        connected = false;
        onClose?.(event);
      });
      socket.addEventListener('error', (event) => {
        connected = false;
        onError?.(event);
      });
      socket.addEventListener('message', (event) => {
        onMessage?.(event);
      });
      return socket;
    } catch (error) {
      connected = false;
      onError?.(error);
      return null;
    }
  }

  function send(payload) {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket is not connected');
    }
    socket.send(JSON.stringify({ destination: cfg.sendDestination, body: payload }));
  }

  function isConnected() {
    return connected && socket?.readyState === WebSocket.OPEN;
  }

  function close() {
    try { socket?.close(); } catch {}
    connected = false;
    socket = null;
  }

  return { connect, send, close, isConnected };
})();
