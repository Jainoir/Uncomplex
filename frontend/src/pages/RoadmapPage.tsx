import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, ApiError, type Progress, type Roadmap, type RoadmapNode } from '../api'

export default function RoadmapPage() {
  const { shareToken } = useParams<{ shareToken: string }>()
  const [roadmap, setRoadmap] = useState<Roadmap | null>(null)
  const [progress, setProgress] = useState<Progress | null>(null)
  const [inLibrary, setInLibrary] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!shareToken) return
    let cancelled = false

    async function load() {
      try {
        const shared = await api.getShared(shareToken!)
        if (cancelled) return
        setRoadmap(shared)

        if (api.isLoggedIn()) {
          const mine = await api.myRoadmaps()
          if (cancelled) return
          const saved = mine.find(m => m.shareToken === shareToken)
          if (saved) {
            setInLibrary(true)
            const withProgress = await api.myRoadmap(saved.roadmapId)
            if (!cancelled) setProgress(withProgress.progress)
          }
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError && err.status === 404
            ? 'This roadmap does not exist (or the link is wrong).'
            : 'Could not load the roadmap. Please try again.')
        }
      }
    }

    load()
    return () => { cancelled = true }
  }, [shareToken])

  async function handleSave() {
    if (!shareToken) return
    const saved = await api.saveToLibrary(shareToken)
    setInLibrary(true)
    setProgress(saved.progress)
  }

  async function toggleNode(node: RoadmapNode, completed: boolean) {
    if (!roadmap || !progress) return
    const updated = await api.setProgress(roadmap.id, node.id, completed)
    setProgress(updated)
  }

  async function copyLink() {
    await navigator.clipboard.writeText(window.location.href)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  if (error) return <section className="roadmap"><p className="error">{error}</p></section>
  if (!roadmap) return <section className="roadmap"><p className="muted">Loading roadmap…</p></section>

  const hours = Math.floor(roadmap.estimatedTotalMinutes / 60)
  const minutes = roadmap.estimatedTotalMinutes % 60
  const completedIds = new Set(progress?.completedNodeIds ?? [])

  return (
    <section className="roadmap">
      <header className="roadmap-header">
        <h1>{roadmap.title}</h1>
        <p className="muted">{roadmap.summary}</p>
        <p className="meta">
          Estimated total: <strong>{hours > 0 ? `${hours} h ` : ''}{minutes > 0 ? `${minutes} min` : ''}</strong>
          {' · '}{roadmap.experienceLevel.toLowerCase()} · {roadmap.goal.toLowerCase().replaceAll('_', ' ')}
        </p>

        <div className="actions">
          <button onClick={copyLink}>{copied ? 'Copied!' : 'Copy sharing link'}</button>
          {api.isLoggedIn() && !inLibrary && (
            <button className="secondary" onClick={handleSave}>Save to my library</button>
          )}
        </div>

        {progress && (
          <div className="progress-wrap" aria-label={`${progress.percent}% complete`}>
            <div className="progress-bar"><div style={{ width: `${progress.percent}%` }} /></div>
            <span>{progress.completedCount}/{progress.totalCount} done ({progress.percent}%)</span>
          </div>
        )}
      </header>

      <ol className="node-list">
        {roadmap.prerequisites.map(node => (
          <NodeCard
            key={node.id}
            node={node}
            trackable={progress !== null}
            completed={completedIds.has(node.id)}
            onToggle={toggleNode}
          />
        ))}
      </ol>
    </section>
  )
}

function NodeCard({ node, trackable, completed, onToggle }: {
  node: RoadmapNode
  trackable: boolean
  completed: boolean
  onToggle: (node: RoadmapNode, completed: boolean) => void
}) {
  const [open, setOpen] = useState(false)

  return (
    <li className={completed ? 'node done' : 'node'}>
      <div className="node-row">
        {trackable && (
          <input
            type="checkbox"
            checked={completed}
            onChange={e => onToggle(node, e.target.checked)}
            aria-label={`Mark ${node.name} as ${completed ? 'not done' : 'done'}`}
          />
        )}
        <button className="node-toggle" onClick={() => setOpen(!open)}>
          <span className="node-name">{node.position}. {node.name}</span>
          <span className="node-meta">{node.estimatedMinutes} min · {node.difficulty.toLowerCase()}</span>
        </button>
      </div>

      {open && (
        <div className="node-detail">
          <p>{node.description}</p>
          <p className="why"><strong>Why first:</strong> {node.reason}</p>
          {node.resources.length > 0 && (
            <ul className="resources">
              {node.resources.map(r => (
                <li key={r.url}>
                  <a href={r.url} target="_blank" rel="noreferrer">{r.title}</a>
                  <span className="badge">{r.sourceType.toLowerCase().replaceAll('_', ' ')}</span>
                  {r.reachable === false && <span className="badge dead">link may be down</span>}
                  <div className="muted small">{r.credibilityReason}</div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </li>
  )
}
