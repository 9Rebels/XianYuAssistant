import type { Account } from '@/types'

type AccountLike = Pick<Account, 'id' | 'accountNote' | 'displayName' | 'unb'>

const GENERATED_NOTE_PATTERN = /^账号_\d+$/

const clean = (value?: string | null) => {
  const text = value?.trim()
  return text || ''
}

export const getAccountDisplayName = (account?: AccountLike | null, fallback = '未命名账号') => {
  if (!account) return fallback

  const note = clean(account.accountNote)
  if (note && !GENERATED_NOTE_PATTERN.test(note)) return note

  return clean(account.displayName)
    || clean(account.unb)
    || (account.id ? `账号${account.id}` : fallback)
}

export const getAccountAvatarText = (account?: AccountLike | null) => {
  return getAccountDisplayName(account, '?').slice(0, 1).toUpperCase()
}
