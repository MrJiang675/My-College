// One refresh for concurrent REST and SSE requests. Never retry an already-started stream.
let pending: Promise<string> | undefined
export async function renewedToken(stale?: string): Promise<string> {
  const { useAuthStore } = await import('@/stores/auth')
  const auth = useAuthStore()
  if (stale && auth.accessToken && auth.accessToken !== stale) return auth.accessToken
  if (!pending)
    pending = auth
      .refresh()
      .then(() => auth.accessToken!)
      .catch((error) => {
        auth.clearSession()
        throw error
      })
      .finally(() => {
        pending = undefined
      })
  return pending
}
