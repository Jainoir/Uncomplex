// API client: typed wrappers over the Uncomplex REST API with JWT handling.
// Access tokens are short-lived; on a 401 the client transparently rotates the
// refresh token once and retries the original request.

const BASE = import.meta.env?.VITE_API_BASE_URL ?? ''

export type ExperienceLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export type LearningGoal =
  | 'GENERAL_UNDERSTANDING'
  | 'BUILD_A_PROJECT'
  | 'SYSTEM_DESIGN_INTERVIEW'
  | 'JOB_INTERVIEW'
  | 'UNIVERSITY_COURSE'

export interface Resource {
  title: string
  url: string
  sourceType: string
  credibilityReason: string
  reachable: boolean | null
}

export interface RoadmapNode {
  id: number
  name: string
  description: string
  reason: string
  difficulty: ExperienceLevel
  estimatedMinutes: number
  position: number
  resources: Resource[]
}

export interface Roadmap {
  id: number
  shareToken: string
  shareUrl: string
  topic: string
  title: string
  summary: string
  experienceLevel: ExperienceLevel
  goal: LearningGoal
  estimatedTotalMinutes: number
  createdAt: string
  prerequisites: RoadmapNode[]
}

export interface Progress {
  completedNodeIds: number[]
  completedCount: number
  totalCount: number
  percent: number
}

export interface RoadmapWithProgress {
  roadmap: Roadmap
  progress: Progress
}

export interface SavedRoadmapSummary {
  roadmapId: number
  topic: string
  title: string
  experienceLevel: ExperienceLevel
  goal: LearningGoal
  estimatedTotalMinutes: number
  shareToken: string
  savedAt: string
  completedNodes: number
  totalNodes: number
}

interface AuthResponse {
  token: string
  expiresAt: string
  refreshToken: string
  refreshExpiresAt: string
  email: string
}

const store = {
  get access() { return localStorage.getItem('uncomplex.access') },
  get refresh() { return localStorage.getItem('uncomplex.refresh') },
  get email() { return localStorage.getItem('uncomplex.email') },
  save(auth: AuthResponse) {
    localStorage.setItem('uncomplex.access', auth.token)
    localStorage.setItem('uncomplex.refresh', auth.refreshToken)
    localStorage.setItem('uncomplex.email', auth.email)
    window.dispatchEvent(new Event('uncomplex:auth'))
  },
  clear() {
    localStorage.removeItem('uncomplex.access')
    localStorage.removeItem('uncomplex.refresh')
    localStorage.removeItem('uncomplex.email')
    window.dispatchEvent(new Event('uncomplex:auth'))
  },
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function raw(path: string, options: RequestInit = {}, withAuth = true): Promise<Response> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (withAuth && store.access) headers['Authorization'] = `Bearer ${store.access}`
  try {
    return await fetch(BASE + path, { ...options, headers,
      signal: options.signal ?? AbortSignal.timeout(120_000) })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'TimeoutError') {
      throw new ApiError(408, 'The request timed out. Please try again.')
    }
    throw error
  }
}

let refreshing: Promise<boolean> | null = null

async function refreshAccess(failedAccess: string | null): Promise<boolean> {
  const rotate = async () => {
    // Another request or tab may have already refreshed while this one waited.
    if (store.access !== failedAccess) return store.access !== null
    const token = store.refresh
    if (!token) return false
    const response = await raw('/api/auth/refresh', {
      method: 'POST', body: JSON.stringify({ refreshToken: token }),
    }, false)
    // A logout or new login during the request must not be overwritten.
    if (store.refresh !== token) return store.access !== null
    if (response.ok) {
      const auth: AuthResponse = await response.json()
      if (store.refresh !== token) return store.access !== null
      store.save(auth)
      return true
    }
    if (response.status === 401) store.clear()
    // Keep the session on transient server/network failures.
    throw new ApiError(response.status, response.status === 401
      ? 'Your session expired. Please log in again.'
      : 'Could not renew your session. Please try again.')
  }
  if (!refreshing) {
    refreshing = (typeof navigator !== 'undefined' && navigator.locks
      ? navigator.locks.request('uncomplex:refresh', rotate)
      : rotate()).finally(() => { refreshing = null })
  }
  return refreshing
}

async function request<T>(path: string, options: RequestInit = {}, withAuth = true): Promise<T> {
  const failedAccess = store.access
  let response = await raw(path, options, withAuth)
  if (withAuth && response.status === 401) {
    if (await refreshAccess(failedAccess)) response = await raw(path, options, true)
  }
  if (!response.ok) {
    let detail = `Request failed (${response.status})`
    try {
      const problem = await response.json()
      detail = problem.detail ?? problem.title ?? detail
    } catch { /* non-JSON error body */ }
    throw new ApiError(response.status, detail)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  currentEmail: () => store.email,
  isLoggedIn: () => store.refresh !== null,

  async register(email: string, password: string) {
    store.save(await request<AuthResponse>('/api/auth/register', {
      method: 'POST', body: JSON.stringify({ email, password }),
    }, false))
  },

  async login(email: string, password: string) {
    store.save(await request<AuthResponse>('/api/auth/login', {
      method: 'POST', body: JSON.stringify({ email, password }),
    }, false))
  },

  async logout() {
    const refreshToken = store.refresh
    store.clear()
    if (refreshToken) {
      await raw('/api/auth/logout', {
        method: 'POST', body: JSON.stringify({ refreshToken }),
      }, false).catch(() => undefined)
    }
  },

  generate(topic: string, experienceLevel: ExperienceLevel, goal: LearningGoal) {
    return request<Roadmap>('/api/roadmaps', {
      method: 'POST', body: JSON.stringify({ topic, experienceLevel, goal }),
    })
  },

  getShared(shareToken: string) {
    return request<Roadmap>(`/api/roadmaps/public/${encodeURIComponent(shareToken)}`, {}, false)
  },

  myRoadmaps() {
    return request<SavedRoadmapSummary[]>('/api/me/roadmaps')
  },

  myRoadmap(roadmapId: number) {
    return request<RoadmapWithProgress>(`/api/me/roadmaps/${roadmapId}`)
  },

  saveToLibrary(shareToken: string) {
    return request<RoadmapWithProgress>('/api/me/roadmaps', {
      method: 'POST', body: JSON.stringify({ shareToken }),
    })
  },

  removeFromLibrary(roadmapId: number) {
    return request<void>(`/api/me/roadmaps/${roadmapId}`, { method: 'DELETE' })
  },

  setProgress(roadmapId: number, nodeId: number, completed: boolean) {
    return request<Progress>(`/api/me/roadmaps/${roadmapId}/nodes/${nodeId}/progress`, {
      method: 'PUT', body: JSON.stringify({ completed }),
    })
  },
}
