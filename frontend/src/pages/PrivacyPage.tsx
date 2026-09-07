const UPDATED = '6 September 2026'
const CONTACT = 'jainoir17@gmail.com'

export default function PrivacyPage() {
  return (
    <section className="legal">
      <h1>Privacy policy</h1>
      <p className="muted">Last updated {UPDATED}</p>

      <p>
        Uncomplex is a personal project that generates prerequisite learning roadmaps. This page
        describes exactly what it collects, why, and how to get rid of it. It describes the system
        as actually built — not a template.
      </p>

      <h2>What is collected</h2>
      <ul>
        <li>
          <strong>Your email address and a hash of your password</strong>, if you create an account.
          Passwords are stored only as a one-way hash; the original is never written down and cannot
          be recovered, only reset.
        </li>
        <li>
          <strong>Which roadmaps you save and which steps you tick off.</strong> That is the entire
          contents of your library.
        </li>
        <li>
          <strong>The topics you type into the generator.</strong> These are sent to Anthropic to
          produce a roadmap, and the resulting roadmap is stored and shared publicly by link.
        </li>
        <li>
          <strong>Your IP address</strong>, held temporarily to enforce request limits so one visitor
          cannot exhaust the service for everyone. It is kept in a short-lived counter, not in a log
          of your activity.
        </li>
      </ul>
      <p>
        There is no analytics, no advertising, no tracking pixels, and no third-party cookies. The
        site stores your login tokens in your browser&apos;s local storage so you stay signed in;
        nothing else is kept there.
      </p>

      <h2>Who else sees it</h2>
      <ul>
        <li>
          <strong>Anthropic</strong> receives the topic, experience level and goal you submit, in
          order to generate the roadmap. It does not receive your email, your account, or anything
          about your library.
        </li>
        <li>
          <strong>Render</strong> (application hosting) and <strong>Neon</strong> (database), both in
          the United States, store the data on my behalf.
        </li>
        <li><strong>Vercel</strong> serves the website itself.</li>
      </ul>
      <p>
        Nothing is sold, rented, or shared with anyone else. Because these providers are located in
        the United States, your information is stored and processed outside Canada, and may be subject
        to access under the laws of that country.
      </p>

      <h2>Generated roadmaps are public</h2>
      <p>
        Every roadmap gets a shareable link that anyone holding it can open, with no account needed.
        Roadmaps are not linked to the person who generated them, and deleting your account does not
        remove them — other people&apos;s libraries and links may depend on them. Do not put anything
        private into a topic.
      </p>

      <h2>How long it is kept</h2>
      <p>
        Account data is kept until you delete your account. Refresh tokens expire after 30 days, and
        are revoked immediately on logout. A rate-limit counter becomes eligible for deletion
        once a full limit window has passed with no further requests from that address, and is
        cleared during a later request; on a service sitting completely idle, an eligible
        counter therefore waits until traffic resumes. Generated roadmaps
        are kept indefinitely, because they are shared content.
      </p>

      <h2>Your choices</h2>
      <ul>
        <li>
          <strong>Delete everything.</strong> Sign in, open <em>My roadmaps</em>, and use{' '}
          <em>Delete my account</em>. This immediately and permanently erases your account, your saved
          library, your progress and your sign-in tokens. It cannot be undone.
        </li>
        <li>
          <strong>Use it without an account.</strong> Generating a roadmap requires no sign-up. If you
          never register, no personal data beyond a temporary rate-limit counter is held at all.
        </li>
        <li>
          <strong>Ask a question, or request access or correction</strong> by emailing the address
          below.
        </li>
      </ul>

      <h2>Contact</h2>
      <p>
        The person responsible for privacy on this project is its author, reachable at{' '}
        <a href={`mailto:${CONTACT}`}>{CONTACT}</a>. If you are in Quebec, you may also contact the
        Commission d&apos;acc&egrave;s &agrave; l&apos;information du Qu&eacute;bec; elsewhere in
        Canada, the Office of the Privacy Commissioner.
      </p>
    </section>
  )
}
