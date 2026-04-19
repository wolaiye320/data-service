import { Button, Popconfirm, Tooltip } from 'antd'
import type { ButtonProps } from 'antd'
import type { ReactNode } from 'react'

type ActionIconButtonProps = {
  icon: ReactNode
  label: string
  onClick?: () => void
  loading?: boolean
  danger?: boolean
  disabled?: boolean
  confirmTitle?: string
  confirmOkText?: string
  confirmCancelText?: string
} & Pick<ButtonProps, 'htmlType'>

export function ActionIconButton({
  icon,
  label,
  onClick,
  loading = false,
  danger = false,
  disabled = false,
  confirmTitle,
  confirmOkText = '确定',
  confirmCancelText = '取消',
  htmlType,
}: ActionIconButtonProps) {
  const button = (
    <Button
      type="text"
      icon={icon}
      aria-label={label}
      data-testid={`action-${label}`}
      loading={loading}
      danger={danger}
      disabled={disabled}
      onClick={confirmTitle ? undefined : onClick}
      htmlType={htmlType}
    />
  )

  if (!confirmTitle) {
    return <Tooltip title={label}>{button}</Tooltip>
  }

  return (
    <Popconfirm
      title={confirmTitle}
      okText={confirmOkText}
      cancelText={confirmCancelText}
      onConfirm={onClick}
    >
      {button}
    </Popconfirm>
  )
}
