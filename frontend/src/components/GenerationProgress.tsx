import { useEffect, useState } from 'react'

/**
 * Generation is a single opaque request — the server cannot report percentages, so
 * neither do we. What it can honestly show is elapsed time and which phase the request
 * is most likely in, plus an explanation once the wait exceeds what users tolerate in
 * silence. Faking a progress bar that jumps to 90% and stalls is worse than no bar.
 */

const STAGES = [
  { after: 0, text: 'Checking whether this roadmap already exists…' },
  { after: 4, text: 'Asking Claude what you need to learn first…' },
  { after: 18, text: 'Working through the prerequisites, in order…' },
  { after: 38, text: 'Still going. Good roadmaps take a moment.' },
  { after: 65, text: 'Longer than usual — the free-tier server may be waking up.' },
]

export default function GenerationProgress({ topic }: { topic: string }) {
  const [elapsed, setElapsed] = useState(0)

  useEffect(() => {
    const started = Date.now()
    const id = setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 500)
    return () => clearInterval(id)
  }, [])

  const stage = STAGES.reduce((current, s) => (elapsed >= s.after ? s : current), STAGES[0])

  return (
    <div className="generating" role="status" aria-live="polite">
      <div className="generating-bar"><div /></div>
      <p className="generating-stage">{stage.text}</p>
      <p className="generating-meta muted small">
        <span className="generating-topic">{topic}</span>
        <span aria-hidden="true"> · </span>
        <span>{elapsed}s elapsed</span>
      </p>
      {elapsed >= 25 && (
        <p className="muted small generating-note">
          A real model call runs behind this, so it usually lands between 30 and 60 seconds.
          You can leave this page — it will not pull you back, and the roadmap is still saved
          if you search for it again.
        </p>
      )}
    </div>
  )
}
