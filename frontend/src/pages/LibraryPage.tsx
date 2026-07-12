import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, type SavedRoadmapSummary } from '../api'

export default function LibraryPage() {
  const navigate = useNavigate()
  const [items, setItems] = useState<SavedRoadmapSummary[] | null>(null)

  useEffect(() => {
    if (!api.isLoggedIn()) {
      navigate('/auth')
      return
    }
    api.myRoadmaps().then(setItems).catch(() => setItems([]))
  }, [navigate])

  async function handleRemove(item: SavedRoadmapSummary) {
    await api.removeFromLibrary(item.roadmapId)
    setItems(current => current?.filter(i => i.roadmapId !== item.roadmapId) ?? null)
  }

  if (items === null) return <section className="library"><p className="muted">Loading…</p></section>

  return (
    <section className="library">
      <h1>My roadmaps</h1>

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
                <button className="link-button danger" onClick={() => handleRemove(item)}>
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
