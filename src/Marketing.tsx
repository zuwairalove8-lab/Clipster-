export function Marketing() {
  return (
    <>
      <div className="brand">
        <div className="brand-mark">C</div>
        <div className="brand-name">CyberTrace</div>
      </div>

      <h1>
        Investigations that <em>ask first</em>.
      </h1>
      <p className="lede">
        CyberTrace turns one link into a documented evidence request: a plain-language consent
        screen, one button per item, and a refusal that is recorded instead of argued with.
      </p>

      <div className="grid">
        <div className="feature">
          <h3>Consent before capture</h3>
          <p>
            The page states who is collecting and why. Camera, microphone and location are only
            requested after the recipient taps their own button — never on load, never batched.
          </p>
        </div>
        <div className="feature">
          <h3>Refusals are first-class</h3>
          <p>
            Declining an item writes a <code>denied</code> record and moves on. The prompt is never
            shown twice, and a denial never blocks the remaining steps.
          </p>
        </div>
        <div className="feature">
          <h3>Evidence that survives</h3>
          <p>
            Each granted item uploads the moment it exists, so a closed tab costs you one step — not
            the whole session. Media lands in a private bucket behind signed URLs.
          </p>
        </div>
        <div className="feature">
          <h3>Live dashboard</h3>
          <p>
            Visits and captures stream into the Android app over Realtime, with filters, a map view
            for coordinates, and a one-tap ZIP export per link and date range.
          </p>
        </div>
      </div>

      <div className="notice">
        <strong>Authorized use only.</strong> CyberTrace is for investigations you are legally
        permitted to run — red-team engagements, fraud cases, or your own accounts. You are
        responsible for the consent, disclosure and retention rules that apply where you operate. It
        cannot and does not read browser history, install software, or capture anything a browser
        permission prompt hasn't approved.
      </div>

      <footer>
        <p>
          Recipients see the consent screen before anything is requested. No trackers, no third-party
          cookies.
        </p>
      </footer>
    </>
  );
}
