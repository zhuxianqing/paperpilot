import { ref } from 'vue';

export type MessageType = 'success' | 'error';

export interface MessageState {
  type: MessageType;
  text: string;
}

export function useMessage(defaultDuration = 3000) {
  const message = ref<MessageState | null>(null);
  let timer: ReturnType<typeof setTimeout> | null = null;

  function clearMessage() {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
    message.value = null;
  }

  function showMessage(text: string, type: MessageType, duration = defaultDuration) {
    clearMessage();
    message.value = { text, type };
    timer = setTimeout(() => {
      message.value = null;
      timer = null;
    }, duration);
  }

  return {
    message,
    showMessage,
    clearMessage
  };
}
