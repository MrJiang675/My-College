import { ref } from 'vue'
import type { DemoPreset } from '@/constants/demoSamples'
export type PresetMode = 'preset-first' | 'fallback-only' | 'off'
const configured = import.meta.env.VITE_DEMO_PRESET_MODE
export const presetMode: PresetMode = ['preset-first', 'fallback-only', 'off'].includes(configured)
  ? configured
  : 'preset-first'
export function useDemoPresetReplay() {
  const replaying = ref(false)
  let controller: AbortController | undefined
  async function replay(preset: DemoPreset, onText: (text: string) => void, signal?: AbortSignal) {
    controller?.abort()
    const current = new AbortController()
    controller = current
    replaying.value = true
    const combined = signal ? AbortSignal.any([signal, current.signal]) : current.signal
    try {
      for (let i = 0; i < preset.answerMarkdown.length; i += 8) {
        combined.throwIfAborted()
        onText(preset.answerMarkdown.slice(0, i + 8))
        await new Promise<void>((resolve, reject) => {
          const done = () => {
            combined.removeEventListener('abort', abort)
            resolve()
          }
          const timer = setTimeout(done, 25)
          const abort = () => {
            clearTimeout(timer)
            combined.removeEventListener('abort', abort)
            reject(new DOMException('已停止演示', 'AbortError'))
          }
          combined.addEventListener('abort', abort, { once: true })
        })
      }
    } finally {
      if (controller === current) {
        controller = undefined
        replaying.value = false
      }
    }
  }
  return { replaying, replay, stop: () => controller?.abort() }
}
