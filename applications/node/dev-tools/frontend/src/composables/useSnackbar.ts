import { ref } from 'vue'

interface Snackbar {
  show: boolean
  message: string
  color?: string
  timeout?: number
  location?: 'top' | 'bottom' | 'start' | 'end' | 'center' | 'top start' | 'top end' | 'bottom start' | 'bottom end'
}

const snackbar = ref<Snackbar>({
  show: false,
  message: '',
  color: 'success',
  timeout: 3000,
  location: 'bottom'
})

export function useSnackbar() {
  const showSuccess = (message: string, timeout = 3000) => {
    snackbar.value = {
      show: true,
      message,
      color: 'success',
      timeout,
      location: 'bottom'
    }
  }

  const showError = (message: string, timeout = 5000) => {
    snackbar.value = {
      show: true,
      message,
      color: 'error',
      timeout,
      location: 'bottom'
    }
  }

  const showInfo = (message: string, timeout = 3000) => {
    snackbar.value = {
      show: true,
      message,
      color: 'info',
      timeout,
      location: 'bottom'
    }
  }

  const showWarning = (message: string, timeout = 4000) => {
    snackbar.value = {
      show: true,
      message,
      color: 'warning',
      timeout,
      location: 'bottom'
    }
  }

  const hide = () => {
    snackbar.value.show = false
  }

  return {
    snackbar,
    showSuccess,
    showError,
    showInfo,
    showWarning,
    hide
  }
}