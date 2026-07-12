import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api'

export default function AuthPage({ onAuthed }: { onAuthed: () => void }) {
  const navigate = useNavigate()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    setError(null)
    try {
      if (mode === 'login') await api.login(email, password)
      else await api.register(email, password)
      onAuthed()
      navigate('/library')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="auth">
      <div className="card">
        <div className="tabs">
          <button
            className={mode === 'login' ? 'tab active' : 'tab'}
            onClick={() => { setMode('login'); setError(null) }}
          >
            Log in
          </button>
          <button
            className={mode === 'register' ? 'tab active' : 'tab'}
            onClick={() => { setMode('register'); setError(null) }}
          >
            Create account
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <label>
            Email
            <input type="email" value={email} onChange={e => setEmail(e.target.value)} required />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              minLength={8}
              required
            />
          </label>
          {mode === 'register' && <p className="hint">At least 8 characters.</p>}

          <button type="submit" disabled={busy}>
            {busy ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}
          </button>
          {error && <p className="error">{error}</p>}
        </form>
      </div>
    </section>
  )
}
