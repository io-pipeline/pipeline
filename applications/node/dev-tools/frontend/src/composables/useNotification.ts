import { ref } from 'vue'

interface Notification {
  show: boolean
  message: string
  type: 'success' | 'error' | 'info' | 'warning'
  timeout?: number
}

const notification = ref<Notification>({
  show: false,
  message: '',
  type: 'info',
  timeout: 3000
})

export function useNotification() {
  const showSuccess = (message: string, timeout = 3000) => {
    notification.value = {
      show: true,
      message,
      type: 'success',
      timeout
    }
  }

  const showError = (message: string, timeout = 5000) => {
    notification.value = {
      show: true,
      message,
      type: 'error',
      timeout
    }
  }

  const showInfo = (message: string, timeout = 3000) => {
    notification.value = {
      show: true,
      message,
      type: 'info',
      timeout
    }
  }

  const showWarning = (message: string, timeout = 4000) => {
    notification.value = {
      show: true,
      message,
      type: 'warning',
      timeout
    }
  }

  const hide = () => {
    notification.value.show = false
  }

  return {
    notification,
    showSuccess,
    showError,
    showInfo,
    showWarning,
    hide
  }
}