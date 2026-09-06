import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, ApiError, type Progress, type Roadmap, type RoadmapNode } from '../api'

export default function RoadmapPage() {
  const { shareToken } = useParams<{ shareToken: string }>()
  return <RoadmapContent key={shareToken} shareToken={shareToken} />
}

function RoadmapContent({ shareToken }: { shareToken: string | undefined }) {
  const [roadmap, setRoadmap] = useState<Roadmap | null>(null)
  const [progress, setProgress] = useState<Progress | null>(null)
  const [inLibrary, setInLibrary] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState(true)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    if (!shareToken) return
    let cancelled = false
    setError(null)
    setActionError(null)
    setProgress(null)
    setInLibrary(false)
    setBusy(true)

    async function load() {
      try {
        const shared = await api.getShared(shareToken!)
        if (cancelled) return
        setRoadmap(shared)

        if (api.isLoggedIn()) {
          try {
            const mine = await api.myRoadmaps()
            if (cancelled) return
            const saved = mine.find(m => m.shareToken === shareToken)
            if (saved) {
              setInLibrary(true)
              const withProgress = await api.myRoadmap(saved.roadmapId)
              if (!cancelled) setProgress(withProgress.progress)
            }
          } catch {
            if (!cancelled) setActionError('The roadmap is available, but your saved progress could not be loaded. Please retry.')
          }
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError && err.status === 404
            ? 'This roadmap does not exist (or the link is wrong).'
            : 'Could not load the roadmap. Please try again.')
        }
      } finally {
        if (!cancelled) setBusy(false)
      }
    }

    load()
    return () => { cancelled = true }
  }, [shareToken, reload])

  async function perform(action: () => Promise<void>, message: string) {
    setBusy(true)
    setActionError(null)
    try { await action() } catch { setActionError(message) } finally { setBusy(false) }
  }

  async function handleSave() {
    if (!shareToken || busy) return
    await perform(async () => {
      const saved = await api.saveToLibrary(shareToken)
      setInLibrary(true)
      setProgress(saved.progress)
    }, 'Could not save this roadmap. Please try again.')
  }

  async function toggleNode(node: RoadmapNode, completed: boolean) {
    if (!roadmap || !progress || busy) return
    await perform(async () => {
      setProgress(await api.setProgress(roadmap.id, node.id, completed))
    }, 'Could not update your progress. Please try again.')
  }

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setActionError('Could not copy the link. You can copy it from your browser address bar.')
    }
  }
  if (error) return <section className="roadmap"><p className="error" role="alert">{error}</p><button onClick={() => setReload(n => n + 1)}>Retry</button></section>
  if (!roadmap) return <section className="roadmap"><p className="muted">Loading roadmap…</p></section>

  const hours = Math.floor(roadmap.estimatedTotalMinutes / 60)
  const minutes = roadmap.estimatedTotalMinutes % 60
  const completedIds = new Set(progress?.completedNodeIds ?? [])

  return (
    <section className="roadmap">
      {actionError && <div role="alert"><p className="error">{actionError}</p>
        <button disabled={busy} onClick={() => setReload(n => n + 1)}>Reload saved progress</button>
      </div>}
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
            <button className="secondary" disabled={busy} onClick={handleSave}>Save to my library</button>
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
            disabled={busy}
            completed={completedIds.has(node.id)}
            onToggle={toggleNode}
          />
        ))}
      </ol>
    </section>
  )
}

function NodeCard({ node, trackable, completed, disabled, onToggle }: {
  node: RoadmapNode
  trackable: boolean
  disabled: boolean
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
            disabled={disabled}
            onChange={e => onToggle(node, e.target.checked)}
            aria-label={`Mark ${node.name} as ${completed ? 'not done' : 'done'}`}
          />
        )}
        <button className="node-toggle" aria-expanded={open} onClick={() => setOpen(!open)}>
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
                  {r.reachable === null && <span className="badge">link not yet checked</span>}
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
