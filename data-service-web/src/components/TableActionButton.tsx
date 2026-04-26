import { Button, Tooltip } from 'antd'
import type { ButtonProps } from 'antd'
import type { ReactNode } from 'react'

type TableActionButtonProps = {
  label: string
  icon: ReactNode
  onClick?: () => void
  danger?: boolean
  disabled?: boolean
  loading?: boolean
  testId?: string
}

export function TableActionButton({
  label,
  icon,
  onClick,
  danger,
  disabled,
  loading,
  testId,
}: TableActionButtonProps) {
  const buttonProps: ButtonProps = {
    type: 'text',
    size: 'small',
    icon,
    'aria-label': label,
    danger,
    disabled,
    loading,
    onClick,
  }

  return (
    <Tooltip title={label}>
      <Button {...buttonProps} data-testid={testId} />
    </Tooltip>
  )
}
