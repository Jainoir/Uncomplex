import { useEffect, useState } from 'react'
import { Link, Route, Routes, useNavigate } from 'react-router-dom'
import { api } from './api'
import LandingPage from './pages/LandingPage'
import RoadmapPage from './pages/RoadmapPage'
import AuthPage from './pages/AuthPage'
import LibraryPage from './pages/LibraryPage'
import PrivacyPage from './pages/PrivacyPage'
import TermsPage from './pages/TermsPage'

export default function App() {
  const navigate = useNavigate()
  const [email, setEmail] = useState<string | null>(api.currentEmail())

  useEffect(() => {
    const sync = () => setEmail(api.currentEmail())
    window.addEventListener('uncomplex:auth', sync)
    window.addEventListener('storage', sync)
    return () => {
      window.removeEventListener('uncomplex:auth', sync)
      window.removeEventListener('storage', sync)
    }
  }, [])

  async function handleLogout() {
    await api.logout()
    setEmail(null)
    navigate('/')
  }

  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="brand">Uncomplex</Link>
        <nav>
          {email ? (
            <>
              <Link to="/library">My roadmaps</Link>
              <span className="user-email">{email}</span>
              <button className="link-button" onClick={handleLogout}>Log out</button>
            </>
          ) : (
            <Link to="/auth">Log in</Link>
          )}
        </nav>
      </header>

      <main>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/r/:shareToken" element={<RoadmapPage key={email ?? "anonymous"} />} />
          <Route path="/auth" element={<AuthPage onAuthed={() => setEmail(api.currentEmail())} />} />
          <Route path="/library" element={<LibraryPage />} />
          <Route path="/privacy" element={<PrivacyPage />} />
          <Route path="/terms" element={<TermsPage />} />
        </Routes>
      </main>

      <footer className="footer">
        Uncomplex — learn what comes first. Roadmaps are AI-generated and may be wrong.
        <span className="footer-links">
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Terms &amp; AI disclosure</Link>
          <a href="https://github.com/Jainoir/Uncomplex">Source</a>
        </span>
      </footer>
    </div>
  )
}
