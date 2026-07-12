// API client: typed wrappers over the Uncomplex REST API with JWT handling.
// Access tokens are short-lived; on a 401 the client transparently rotates the
// refresh token once and retries the original request.

const BASE = import.meta.env.VITE_API_BASE_URL ?? ''

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
  },
  clear() {
    localStorage.removeItem('uncomplex.access')
    localStorage.removeItem('uncomplex.refresh')
    localStorage.removeItem('uncomplex.email')
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
  return fetch(BASE + path, { ...options, headers })
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response = await raw(path, options)

  // Expired access token? Rotate the refresh token once and retry.
  if (response.status === 401 && store.refresh && !path.startsWith('/api/auth/')) {
    const refreshed = await raw('/api/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: store.refresh }),
    }, false)
    if (refreshed.ok) {
      store.save(await refreshed.json())
      response = await raw(path, options)
    } else {
      store.clear()
    }
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
    }))
  },

  async login(email: string, password: string) {
    store.save(await request<AuthResponse>('/api/auth/login', {
      method: 'POST', body: JSON.stringify({ email, password }),
    }))
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
    return request<Roadmap>(`/api/roadmaps/public/${shareToken}`)
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
