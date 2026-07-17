<script lang="ts">
  import { onMount } from 'svelte'
  import logo from './assets/logo.png'
  import { appVersion, openGitHub } from './lib/bridge'
  import Play from './views/Play.svelte'
  import Settings from './views/Settings.svelte'
  import About from './views/About.svelte'

  type View = 'play' | 'settings' | 'about'
  let view: View = 'play'
  let version = ''

  const tabs: { id: View; label: string }[] = [
    { id: 'play', label: 'Play' },
    { id: 'settings', label: 'Settings' },
    { id: 'about', label: 'About' },
  ]

  onMount(async () => {
    try {
      version = await appVersion()
    } catch {
      // Running outside Wails (a plain browser preview) has no bound backend; the shell still
      // renders so the layout can be worked on, it just has no version to show.
      version = ''
    }
  })
</script>

<div class="shell">
  <nav class="sidebar">
    <div class="brand">
      <img src={logo} alt="Esdeath: Cryostasis" />
      <div class="wordmark">
        <span class="title">ESDEATH</span>
        <span class="subtitle">Cryostasis</span>
      </div>
    </div>

    <div class="tabs">
      {#each tabs as tab}
        <button
          class="tab"
          class:active={view === tab.id}
          on:click={() => (view = tab.id)}
        >
          {tab.label}
        </button>
      {/each}
    </div>

    <div class="foot">
      <button class="github" on:click={openGitHub} title="Open the project on GitHub">
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
          <path
            fill="currentColor"
            d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38
               0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01
               1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95
               0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27
               2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82
               1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01
               2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"
          />
        </svg>
        <span>GitHub</span>
      </button>
      {#if version}
        <span class="version">Launcher v{version}</span>
      {/if}
    </div>
  </nav>

  <main class="content">
    {#if view === 'play'}
      <Play />
    {:else if view === 'settings'}
      <Settings />
    {:else}
      <About />
    {/if}
  </main>
</div>

<style>
  .shell {
    display: flex;
    height: 100%;
  }

  .sidebar {
    width: var(--sidebar);
    flex-shrink: 0;
    background: var(--header);
    border-right: 1px solid rgba(90, 143, 199, 0.14);
    display: flex;
    flex-direction: column;
    padding: 22px 16px 16px;
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 4px 6px 22px;
  }

  .brand img {
    width: 44px;
    height: 44px;
    object-fit: contain;
    filter: drop-shadow(0 0 6px rgba(90, 143, 199, 0.45));
  }

  .wordmark {
    display: flex;
    flex-direction: column;
    line-height: 1.1;
  }

  .title {
    font-weight: 700;
    letter-spacing: 2px;
    font-size: 15px;
  }

  .subtitle {
    color: var(--accent);
    font-size: 12px;
    letter-spacing: 1px;
  }

  .tabs {
    display: flex;
    flex-direction: column;
    gap: 4px;
    margin-top: 6px;
  }

  .tab {
    text-align: left;
    padding: 11px 14px;
    border-radius: var(--radius);
    color: var(--subtext);
    font-size: 14px;
    font-weight: 600;
    border-left: 3px solid transparent;
    transition: background 0.12s, color 0.12s;
  }

  .tab:hover {
    background: var(--row-hover);
    color: var(--text);
  }

  .tab.active {
    background: var(--row);
    color: var(--text);
    border-left-color: var(--accent);
  }

  .foot {
    margin-top: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .github {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 9px 14px;
    width: 100%;
    border-radius: var(--radius);
    background: var(--row);
    color: var(--subtext);
    font-size: 13px;
    font-weight: 600;
    transition: background 0.12s, color 0.12s;
  }

  .github:hover {
    background: var(--row-hover);
    color: var(--text);
  }

  .version {
    color: var(--subtext);
    font-size: 11px;
    padding-left: 4px;
  }

  .content {
    flex: 1;
    min-width: 0;
    background: linear-gradient(160deg, var(--bg-top), var(--bg-bottom));
    overflow-y: auto;
  }
</style>
