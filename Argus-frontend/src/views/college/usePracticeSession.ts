import { reactive, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { PracticeSimulateRequest, SimulationResult } from '@/api/simulation'
import type { Model } from './models'
// Route-to-route state only: no image/base64/HTML in localStorage, workspace JSON or server database.
const session = reactive({
  owner: undefined as number | undefined,
  draft: { reasoningMode: 'fast' } as PracticeSimulateRequest,
  fileName: '',
  mode: 'ai' as 'ai' | 'builtin',
  model: undefined as Model | undefined,
  result: undefined as SimulationResult | undefined,
  snapshots: [] as { title: string; model: Model }[],
})
export function usePracticeSession() {
  const auth = useAuthStore()
  watch(
    () => auth.currentUser?.userId,
    (owner) => {
      if (owner !== session.owner) {
        session.owner = owner
        session.draft = { reasoningMode: 'fast' }
        session.fileName = ''
        session.result = undefined
        session.model = undefined
        session.snapshots = []
        session.mode = 'ai'
      }
    },
    { immediate: true },
  )
  return session
}
export function hasPracticeInput(r: PracticeSimulateRequest) {
  return !!(
    r.experimentName?.trim() ||
    r.simulationGoal?.trim() ||
    r.materialText?.trim() ||
    r.imageDataUrl
  )
}
