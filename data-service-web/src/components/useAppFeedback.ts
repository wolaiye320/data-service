import { App } from 'antd'

export function useAppFeedback() {
  const { message, notification, modal } = App.useApp()
  return { message, notification, modal }
}
