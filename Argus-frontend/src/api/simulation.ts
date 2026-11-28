import http, { authenticatedFetch } from './http'
import type { CampusProfile } from '@/types/notice'
export type PracticeSimulateRequest = {
  experimentName?: string
  simulationGoal?: string
  materialText?: string
  imageDataUrl?: string
  reasoningMode: 'fast' | 'deep'
  campusProfile?: CampusProfile
}
export type SimulationResult = {
  title: string
  summary: string
  html: string
  usedModel: string
  reasoningMode: string
  fixSummary?: string
}
export type SimulationEvent = {
  event: 'status' | 'delta' | 'done' | 'error'
  data: Record<string, unknown>
}
export async function simulateStream(
  payload: PracticeSimulateRequest,
  token: string,
  onEvent: (e: SimulationEvent) => void,
  signal: AbortSignal,
) {
  const response = await authenticatedFetch('/college/practice/simulate-stream', token, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal,
  })
  if (!response.ok) {
    let message = '生成失败，请重试'
    try {
      message = (await response.json()).message || message
    } catch {}
    throw Error(message)
  }
  if (!response.body) throw Error('未收到流式响应')
  const reader = response.body.getReader(),
    decoder = new TextDecoder()
  let buffer = '',
    terminal = false
  function frame(raw: string) {
    let event = '',
      data = ''
    for (const line of raw.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) data += line.slice(5).trimStart() + '\n'
    }
    if (!data.trim() || !['status', 'delta', 'done', 'error'].includes(event)) return
    const parsed = JSON.parse(data)
    if (event === 'done' || event === 'error') terminal = true
    onEvent({ event: event as SimulationEvent['event'], data: parsed })
  }
  try {
    while (!terminal) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done }).replace(/\r/g, '')
      if (buffer.length > 600000) throw Error('仿真响应过大，请简化实验')
      let index
      while ((index = buffer.indexOf('\n\n')) >= 0) {
        frame(buffer.slice(0, index))
        buffer = buffer.slice(index + 2)
        if (terminal) break
      }
      if (done) {
        if (buffer.trim()) frame(buffer)
        break
      }
    }
    if (!terminal && !signal.aborted) throw Error('连接提前中断，请重新生成或使用内置模型')
  } finally {
    await reader.cancel().catch(() => {})
  }
}
export async function repairSimulation(
  request: {
    experimentName?: string
    simulationGoal?: string
    brokenHtml: string
    runtimeError: string
  },
  signal?: AbortSignal,
) {
  const { data } = await http.post('/college/practice/repair-simulation', request, {
    timeout: 210000,
    signal,
  })
  return data.data as SimulationResult
}
