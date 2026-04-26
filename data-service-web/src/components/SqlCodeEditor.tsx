import { forwardRef, useMemo, useRef, type TextareaHTMLAttributes, type UIEvent } from 'react'

type SqlCodeEditorProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'value'> & {
  value?: string
}

type TokenType = 'keyword' | 'comment' | 'string' | 'number' | 'identifier' | 'plain'

type HighlightToken = {
  text: string
  type: TokenType
}

const SQL_KEYWORDS = [
  'select',
  'from',
  'where',
  'and',
  'or',
  'insert',
  'into',
  'update',
  'delete',
  'join',
  'left',
  'right',
  'inner',
  'outer',
  'on',
  'group',
  'by',
  'order',
  'having',
  'limit',
  'offset',
  'union',
  'all',
  'distinct',
  'as',
  'case',
  'when',
  'then',
  'else',
  'end',
  'in',
  'exists',
  'not',
  'null',
  'is',
  'like',
  'between',
  'with',
  'over',
  'partition',
  'create',
  'alter',
  'drop',
  'table',
  'view',
  'database',
  'schema',
  'values',
  'set',
  'asc',
  'desc',
  'count',
  'sum',
  'avg',
  'min',
  'max',
  'cast',
  'coalesce',
]

const TOKEN_REGEX = new RegExp(
  [
    '--.*?$',
    '/\\*[\\s\\S]*?\\*/',
    "'(?:''|[^'])*'",
    '"(?:[^"]|"")*"',
    '\\b\\d+(?:\\.\\d+)?\\b',
    `\\b(?:${SQL_KEYWORDS.join('|')})\\b`,
  ].join('|'),
  'gim',
)

function getTokenType(token: string): TokenType {
  if (token.startsWith('--') || token.startsWith('/*')) {
    return 'comment'
  }
  if (token.startsWith("'")) {
    return 'string'
  }
  if (token.startsWith('"')) {
    return 'identifier'
  }
  if (/^\d+(?:\.\d+)?$/u.test(token)) {
    return 'number'
  }
  if (SQL_KEYWORDS.includes(token.toLowerCase())) {
    return 'keyword'
  }
  return 'plain'
}

function tokenizeSql(value: string): HighlightToken[] {
  if (!value) {
    return []
  }

  const tokens: HighlightToken[] = []
  let lastIndex = 0

  for (const match of value.matchAll(TOKEN_REGEX)) {
    const index = match.index ?? 0
    const text = match[0]

    if (index > lastIndex) {
      tokens.push({ text: value.slice(lastIndex, index), type: 'plain' })
    }

    tokens.push({ text, type: getTokenType(text) })
    lastIndex = index + text.length
  }

  if (lastIndex < value.length) {
    tokens.push({ text: value.slice(lastIndex), type: 'plain' })
  }

  return tokens
}

export const SqlCodeEditor = forwardRef<HTMLTextAreaElement, SqlCodeEditorProps>(function SqlCodeEditor(
  { className, onChange, onScroll, placeholder, rows = 8, value, disabled, ...rest },
  ref,
) {
  const highlightRef = useRef<HTMLPreElement>(null)
  const displayValue = value ?? ''
  const tokens = useMemo(() => tokenizeSql(displayValue), [displayValue])

  const handleScroll = (event: UIEvent<HTMLTextAreaElement>) => {
    if (highlightRef.current) {
      const target = event.currentTarget
      highlightRef.current.scrollTop = target.scrollTop
      highlightRef.current.scrollLeft = target.scrollLeft
    }
    onScroll?.(event)
  }

  return (
    <div
      className={`sql-code-editor${className ? ` ${className}` : ''}${disabled ? ' sql-code-editor--disabled' : ''}`}
      data-testid="sql-code-editor"
    >
      <pre
        ref={highlightRef}
        className="sql-code-editor__highlight"
        aria-hidden="true"
        style={{ minHeight: `${rows * 1.6}em` }}
      >
        {displayValue ? (
          tokens.map((token, index) => (
            <span key={`${token.type}-${index}`} className={`sql-code-editor__token sql-code-editor__token--${token.type}`}>
              {token.text}
            </span>
          ))
        ) : (
          <span className="sql-code-editor__placeholder">{placeholder}</span>
        )}
        {displayValue.endsWith('\n') ? '\n' : null}
      </pre>
      <textarea
        {...rest}
        ref={ref}
        value={displayValue}
        rows={rows}
        placeholder={placeholder}
        disabled={disabled}
        spellCheck={false}
        className="sql-code-editor__input"
        onChange={onChange}
        onScroll={handleScroll}
      />
    </div>
  )
})
