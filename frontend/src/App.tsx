import { useState } from 'react'
import { Link, Route, Routes, useNavigate } from 'react-router-dom'
import { api } from './api'
import LandingPage from './pages/LandingPage'
import RoadmapPage from './pages/RoadmapPage'
import AuthPage from './pages/AuthPage'
import LibraryPage from './pages/LibraryPage'

export default function App() {
  const navigate = useNavigate()
  const [email, setEmail] = useState<string | null>(api.currentEmail())

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
          <Route path="/r/:shareToken" element={<RoadmapPage />} />
          <Route path="/auth" element={<AuthPage onAuthed={() => setEmail(api.currentEmail())} />} />
          <Route path="/library" element={<LibraryPage />} />
        </Routes>
      </main>

      <footer className="footer">
        Uncomplex — learn what comes first. <a href="https://github.com/Jainoir/Uncomplex">Source</a>
      </footer>
    </div>
  )
}
