import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError, type ExperienceLevel, type LearningGoal } from '../api'

const LEVELS: { value: ExperienceLevel; label: string }[] = [
  { value: 'BEGINNER', label: 'Beginner' },
  { value: 'INTERMEDIATE', label: 'Intermediate' },
  { value: 'ADVANCED', label: 'Advanced' },
]

const GOALS: { value: LearningGoal; label: string }[] = [
  { value: 'GENERAL_UNDERSTANDING', label: 'General understanding' },
  { value: 'BUILD_A_PROJECT', label: 'Build a project' },
  { value: 'SYSTEM_DESIGN_INTERVIEW', label: 'System design interview' },
  { value: 'JOB_INTERVIEW', label: 'Job interview' },
  { value: 'UNIVERSITY_COURSE', label: 'University course' },
]

export default function LandingPage() {
  const navigate = useNavigate()
  const [topic, setTopic] = useState('')
  const [level, setLevel] = useState<ExperienceLevel>('BEGINNER')
  const [goal, setGoal] = useState<LearningGoal>('GENERAL_UNDERSTANDING')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!topic.trim() || busy) return
    setBusy(true)
    setError(null)
    try {
      const roadmap = await api.generate(topic.trim(), level, goal)
      navigate(`/r/${roadmap.shareToken}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="landing">
      <h1>What do you want to understand?</h1>
      <p className="tagline">
        Uncomplex doesn't teach you the topic — it tells you what to learn <em>first</em>,
        in order, with credible resources.
      </p>

      <form className="card generate-form" onSubmit={handleSubmit}>
        <label>
          Topic
          <input
            value={topic}
            onChange={e => setTopic(e.target.value)}
            placeholder="Rate limiting, Docker, OAuth, database indexing…"
            maxLength={120}
            required
          />
        </label>

        <div className="form-row">
          <label>
            Current level
            <select value={level} onChange={e => setLevel(e.target.value as ExperienceLevel)}>
              {LEVELS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </label>

          <label>
            Learning goal
            <select value={goal} onChange={e => setGoal(e.target.value as LearningGoal)}>
              {GOALS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </label>
        </div>

        <button type="submit" disabled={busy}>
          {busy ? 'Building your path…' : 'Build my learning path'}
        </button>

        {error && <p className="error">{error}</p>}
      </form>
    </section>
  )
}
