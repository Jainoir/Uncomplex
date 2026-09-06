import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, type SavedRoadmapSummary } from '../api'

export default function LibraryPage() {
  const navigate = useNavigate()
  const [items, setItems] = useState<SavedRoadmapSummary[] | null>(null)

  const [error, setError] = useState<string | null>(null)
  const [removing, setRemoving] = useState<number | null>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    if (!api.isLoggedIn()) {
      navigate('/auth')
      return
    }
    let cancelled = false
    setError(null)
    setItems(null)
    api.myRoadmaps().then(result => { if (!cancelled) setItems(result) })
      .catch(() => { if (!cancelled) setError('Could not load your library. Please try again.') })
    return () => { cancelled = true }
  }, [navigate, reload])

  async function handleRemove(item: SavedRoadmapSummary) {
    setRemoving(item.roadmapId)
    setError(null)
    try {
      await api.removeFromLibrary(item.roadmapId)
      setItems(current => current?.filter(i => i.roadmapId !== item.roadmapId) ?? null)
    } catch {
      setError('Could not remove this roadmap. Please try again.')
    } finally {
      setRemoving(null)
    }
  }

  if (items === null && error) return <section className="library">
    <p className="error" role="alert">{error}</p>
    <button onClick={() => setReload(value => value + 1)}>Retry</button>
  </section>
  if (items === null) return <section className="library"><p className="muted">Loading…</p></section>

  return (
    <section className="library">
      <h1>My roadmaps</h1>
      {error && <p className="error" role="alert">{error}</p>}

      {items.length === 0 ? (
        <p className="muted">
          Nothing saved yet. <Link to="/">Generate a roadmap</Link> — it lands here automatically.
        </p>
      ) : (
        <ul className="library-list">
          {items.map(item => {
            const percent = item.totalNodes === 0
              ? 0
              : Math.round((100 * item.completedNodes) / item.totalNodes)
            return (
              <li key={item.roadmapId} className="card library-item">
                <Link to={`/r/${item.shareToken}`} className="library-title">
                  {item.title}
                </Link>
                <div className="progress-wrap">
                  <div className="progress-bar"><div style={{ width: `${percent}%` }} /></div>
                  <span>{item.completedNodes}/{item.totalNodes}</span>
                </div>
                <div className="library-meta muted small">
                  {item.experienceLevel.toLowerCase()} · {item.goal.toLowerCase().replaceAll('_', ' ')}
                  {' · '}{item.estimatedTotalMinutes} min total
                </div>
                <button className="link-button danger" disabled={removing !== null} onClick={() => handleRemove(item)}>
                  Remove
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
