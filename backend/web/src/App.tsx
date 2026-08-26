import { LivePanel } from './components/LivePanel'

const REPO = 'https://gitlab.ramon.moe/4G0NYY/Esdeath-Cryostasis'
const RELEASES = `${REPO}/-/releases/permalink/latest`

/**
 * The module categories as the in-game menu groups them, with the honest one-line summary each
 * category deserves. Counts are written out rather than derived: the catalogue lives in the mod,
 * not in this service, and a number pulled from thin air would be worse than one that has to be
 * updated alongside the module it describes.
 */
const CATEGORIES = [
  {
    name: 'HUD',
    count: 10,
    blurb:
      'Anchored, draggable overlay elements. Every one of them is yours alone: nothing here is sent anywhere or visible to anyone else.',
    modules: ['FPS', 'CPS', 'XYZ', 'ReachDisplay', 'PingTag', 'MLGHelper', 'ArrayList', 'OnlineList'],
  },
  {
    name: 'Render',
    count: 10,
    blurb:
      'Changes to what your own client draws. Zoom and Freecam never leave your machine; Xray and Nightvision are the kind a server cannot see but will not thank you for.',
    modules: ['Zoom', 'Freecam', 'Freelook', 'Hitbox', 'Xray', 'Nightvision', 'StatusTag'],
  },
  {
    name: 'Movement',
    count: 9,
    blurb:
      'Motion the vanilla client would not produce. Most of it only holds up in singleplayer, where the integrated server shares your player and agrees with whatever the client decided.',
    modules: ['Fly', 'Spider', 'Jesus', 'SafeWalk', 'AutoPath', 'NoCobweb', 'Zoot'],
  },
  {
    name: 'Combat',
    count: 5,
    blurb:
      'Killaura, Reach and their neighbours. This is the half a fair-play server exists to reject, and it will. The docs say so per module rather than burying it.',
    modules: ['Killaura', 'Reach', 'AutoDodge', 'Sharpness', 'MoreParticles'],
  },
  {
    name: 'Player',
    count: 5,
    blurb:
      'Inventory and interaction helpers that act on your behalf, at the speed and cadence you set with the sliders.',
    modules: ['AutoTool', 'AutoEquip', 'AutoTotem', 'FastBreak', 'NoHunger'],
  },
  {
    name: 'Misc',
    count: 5,
    blurb:
      'The pieces that talk to this backend: cosmetics, ranks, presence, and a chat channel shared by every Cryostasis client whatever server they are on.',
    modules: ['GlobalChat', 'DiscordPresence', 'TabGui', 'AutoText', 'TakeAll'],
  },
]

function Mark() {
  return (
    <svg width="22" height="22" viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="6" fill="#0E1B2B" />
      <path
        d="M16 5v22M6.5 10.5l19 11M25.5 10.5l-19 11"
        stroke="#5A8FC7"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
    </svg>
  )
}

/** One slider in the mocked menu, drawn at the position its value implies. */
function MockSlider({ label, value, fraction }: { label: string; value: string; fraction: number }) {
  const percent = `${Math.round(fraction * 100)}%`
  return (
    <div className="mock-slider">
      <div className="label">
        <span>{label}</span>
        <b>{value}</b>
      </div>
      <div className="track">
        <div className="fill" style={{ width: percent }} />
        <div className="handle" style={{ left: percent }} />
      </div>
    </div>
  )
}

export default function App() {
  return (
    <>
      <header className="topbar">
        <div className="shell">
          <a className="brand" href="#top">
            <Mark />
            Esdeath: Cryostasis
          </a>
          <nav>
            <a href="#modules">Modules</a>
            <a href="#live">Live</a>
            <a href="#install">Install</a>
            <a className="hide-sm" href={REPO}>
              Source
            </a>
          </nav>
        </div>
      </header>

      <main id="top">
        <section className="hero">
          <div className="shell">
            <div>
              <p className="eyebrow">Fabric mod · Minecraft 1.21.x</p>
              <h1>
                The client that went <span className="cold">cold</span>, rebuilt.
              </h1>
              <p className="lede">
                EsdeathClient was a 1.8.9 client whose backend went dark and whose source was never
                published. Cryostasis is that client recovered from its own decompilation and
                rebuilt as a Fabric mod: forty-four modules, cosmetics and ranks served from a
                backend of its own, and a chat channel every client shares.
              </p>
              <div className="hero-actions">
                <a className="button primary" href={RELEASES}>
                  Download the latest release
                </a>
                <a className="button" href={REPO}>
                  Read the source
                </a>
              </div>
              <p className="hero-meta">
                No Minecraft code lives in the repository. The build ships as a mod jar; the
                installer downloads Fabric on your own machine.
              </p>
            </div>

            <div className="mock" aria-hidden="true">
              <div className="mock-head">Combat</div>
              <div className="mock-body">
                <div className="mock-row on">
                  <span>Killaura</span>
                  <span className="key">R</span>
                </div>
                <MockSlider label="Reach" value="4.20" fraction={0.13} />
                <MockSlider label="Hits Per Second" value="12" fraction={0.58} />
                <div className="mock-row">
                  <span>AutoDodge</span>
                </div>
                <div className="mock-row on">
                  <span>Reach</span>
                </div>
                <MockSlider label="Entities" value="5.10" fraction={0.23} />
              </div>
            </div>
          </div>
        </section>

        <section id="modules">
          <div className="shell">
            <p className="eyebrow">Modules</p>
            <h2>Forty-four, across six categories</h2>
            <p className="lede">
              Every configurable value is a slider you drag, a mode you cycle, or a key you bind,
              all in one menu on Right Shift. Settings persist to versioned JSON, so a config
              survives an update.
            </p>

            <div className="grid">
              {CATEGORIES.map((category) => (
                <article className="card" key={category.name}>
                  <h3>
                    {category.name}
                    <span className="count">{category.count} modules</span>
                  </h3>
                  <p>{category.blurb}</p>
                  <div className="tags">
                    {category.modules.map((module) => (
                      <span className="tag" key={module}>
                        {module}
                      </span>
                    ))}
                  </div>
                </article>
              ))}
            </div>

            <div className="caveat">
              <p>
                <strong>These are not all the same kind of thing.</strong> Some modules never leave
                your client. Some only hold up in singleplayer, where the integrated server shares
                your player and agrees with whatever the client decided. The rest is the kind a
                fair-play server exists to reject, and it will.
              </p>
              <p>
                The documentation says which is which, module by module, rather than presenting a
                feature list and letting you find out on someone else's server.
              </p>
            </div>
          </div>
        </section>

        <LivePanel />

        <section id="install">
          <div className="shell">
            <p className="eyebrow">Install</p>
            <h2>Windows: one download</h2>
            <p className="lede">
              The setup installer places the desktop launcher in your programs folder, with a Start
              Menu shortcut and an Add/Remove Programs entry, and no administrator rights. The
              launcher takes over from there and keeps the mod up to date itself.
            </p>

            <div className="steps">
              <div className="step">
                <h3>Get the setup</h3>
                <p>
                  Download <code>EsdeathCryostasisSetup.exe</code> from the latest release and run
                  it.
                </p>
              </div>
              <div className="step">
                <h3>Let the launcher install</h3>
                <p>
                  It downloads Fabric and the mod jar onto your machine and writes a launch profile
                  pointed at this backend.
                </p>
              </div>
              <div className="step">
                <h3>Press Right Shift</h3>
                <p>
                  Launch Minecraft on the new profile, join a world, and the module menu opens on
                  Right Shift. Right Ctrl opens cosmetics and presence.
                </p>
              </div>
            </div>

            <p className="note">
              On Linux and macOS there is no setup binary: run the launcher directly, or drop the
              mod jar into <code>.minecraft/mods</code> alongside Fabric API. Both are attached to
              every release.
            </p>
          </div>
        </section>
      </main>

      <footer>
        <div className="shell">
          <span>Esdeath: Cryostasis · a personal client, built in the open.</span>
          <nav>
            <a href={REPO}>GitLab</a>
            <a href={`${REPO}/-/tree/main/docs`}>Docs</a>
            <a href={RELEASES}>Releases</a>
            <a href="/api/version">API</a>
          </nav>
        </div>
      </footer>
    </>
  )
}
