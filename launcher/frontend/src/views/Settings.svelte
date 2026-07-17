<script lang="ts">
  import { onMount } from 'svelte'
  import {
    getConfig,
    saveConfig,
    testBackend,
    selectMinecraftDir,
    type BackendStatus,
  } from '../lib/bridge'

  // Mirrors DefaultBackendURL in launcher/config.go. Kept here only for the Reset button; the
  // real default is applied by the Go side, this is just the same string for the UI affordance.
  const DEFAULT_BACKEND = 'https://cryostasis.ramon.moe/api'

  let backendUrl = ''
  let minecraftDir = ''
  let minecraftVersion = ''
  let schemaVersion = 1

  let testing = false
  let test: BackendStatus | null = null
  let saving = false
  let saved = false
  let error = ''

  onMount(async () => {
    try {
      const cfg = await getConfig()
      backendUrl = cfg.backendUrl
      minecraftDir = cfg.minecraftDir
      minecraftVersion = cfg.minecraftVersion
      schemaVersion = cfg.schemaVersion
    } catch {
      backendUrl = DEFAULT_BACKEND
    }
  })

  // Editing any field invalidates a stale "Saved" note and a prior test result, so the badges
  // never claim more than the current values were checked against.
  function touched() {
    saved = false
    error = ''
  }

  async function onTest() {
    testing = true
    test = null
    try {
      test = await testBackend(backendUrl)
    } catch (e) {
      test = { ok: false, version: '', message: String(e) }
    } finally {
      testing = false
    }
  }

  async function onBrowse() {
    try {
      const dir = await selectMinecraftDir()
      if (dir) {
        minecraftDir = dir
        touched()
      }
    } catch {
      // Dialog cancelled or unavailable: leave the field as it was.
    }
  }

  async function onSave() {
    saving = true
    error = ''
    try {
      const stored = await saveConfig({
        schemaVersion,
        backendUrl,
        minecraftDir,
        minecraftVersion,
      })
      backendUrl = stored.backendUrl
      minecraftDir = stored.minecraftDir
      minecraftVersion = stored.minecraftVersion
      saved = true
      test = null
    } catch (e) {
      error = String(e)
    } finally {
      saving = false
    }
  }

  function resetBackend() {
    backendUrl = DEFAULT_BACKEND
    test = null
    touched()
  }
</script>

<div class="page">
  <header>
    <h1>Settings</h1>
    <p class="lede">Point the client at any backend. The default is the hosted instance.</p>
  </header>

  <section class="card">
    <label for="backend">Backend URL</label>
    <p class="hint">
      The address the client reads its cosmetics and presence from. Self-hosting? Enter your own
      instance here (include the <code>/api</code> path). This is pinned into the launch profile as
      <code>-Dcryostasis.api</code>, so it takes effect the next time you play.
    </p>
    <div class="row">
      <input
        id="backend"
        type="text"
        bind:value={backendUrl}
        on:input={touched}
        spellcheck="false"
        placeholder={DEFAULT_BACKEND}
      />
      <button class="ghost" on:click={onTest} disabled={testing}>
        {testing ? 'Testing...' : 'Test connection'}
      </button>
    </div>
    <div class="under">
      <button class="link" on:click={resetBackend}>Reset to default</button>
      {#if test}
        <span class="badge" class:ok={test.ok} class:bad={!test.ok}>{test.message}</span>
      {/if}
    </div>
  </section>

  <section class="card">
    <label for="mcdir">Minecraft folder</label>
    <p class="hint">Leave blank to use the standard <code>.minecraft</code> location for your system.</p>
    <div class="row">
      <input
        id="mcdir"
        type="text"
        bind:value={minecraftDir}
        on:input={touched}
        spellcheck="false"
        placeholder="Auto-detect"
      />
      <button class="ghost" on:click={onBrowse}>Browse</button>
    </div>
  </section>

  <section class="card">
    <label for="mcver">Minecraft version</label>
    <p class="hint">Leave blank to target the version this build of the mod is made for.</p>
    <input
      id="mcver"
      class="narrow"
      type="text"
      bind:value={minecraftVersion}
      on:input={touched}
      spellcheck="false"
      placeholder="Default"
    />
  </section>

  <section class="save">
    <button class="primary" on:click={onSave} disabled={saving}>
      {saving ? 'Saving...' : 'Save settings'}
    </button>
    {#if saved}<span class="badge ok">Saved.</span>{/if}
    {#if error}<span class="badge bad">{error}</span>{/if}
  </section>
</div>

<style>
  .page {
    padding: 30px 34px;
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  header h1 {
    margin: 0;
    font-size: 26px;
    letter-spacing: 0.5px;
  }

  .lede {
    margin: 6px 0 0;
    color: var(--subtext);
    font-size: 14px;
  }

  .card {
    background: var(--panel);
    border: 1px solid rgba(90, 143, 199, 0.12);
    border-radius: var(--radius);
    padding: 18px 20px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  label {
    font-size: 14px;
    font-weight: 700;
    letter-spacing: 0.3px;
  }

  .hint {
    margin: 0;
    color: var(--subtext);
    font-size: 13px;
    line-height: 1.5;
  }

  code {
    font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
    background: var(--cell);
    border: 1px solid var(--cell-border);
    border-radius: 4px;
    padding: 1px 5px;
    font-size: 12px;
    color: var(--text);
  }

  .row {
    display: flex;
    gap: 10px;
    margin-top: 4px;
  }

  input {
    flex: 1;
    background: var(--field);
    border: 1px solid var(--cell-border);
    border-radius: var(--radius);
    color: var(--text);
    font-size: 14px;
    padding: 11px 13px;
    font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
    outline: none;
    transition: border-color 0.12s;
    user-select: text;
  }

  input:focus {
    border-color: var(--accent);
  }

  input.narrow {
    max-width: 220px;
  }

  .under {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-top: 8px;
    flex-wrap: wrap;
  }

  .ghost {
    background: var(--row);
    border: 1px solid rgba(90, 143, 199, 0.16);
    color: var(--text);
    font-weight: 600;
    font-size: 13px;
    padding: 11px 16px;
    border-radius: var(--radius);
    white-space: nowrap;
    transition: background 0.12s;
  }

  .ghost:hover:not(:disabled) {
    background: var(--row-hover);
  }

  .link {
    color: var(--accent);
    font-size: 13px;
    font-weight: 600;
    padding: 0;
  }

  .link:hover {
    text-decoration: underline;
  }

  .save {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-top: 4px;
  }

  .primary {
    background: var(--accent);
    color: #06111d;
    font-weight: 700;
    font-size: 14px;
    padding: 12px 30px;
    border-radius: var(--radius);
    transition: filter 0.12s;
  }

  .primary:hover:not(:disabled) {
    filter: brightness(1.08);
  }

  button:disabled {
    opacity: 0.55;
    cursor: default;
  }

  .badge {
    font-size: 13px;
    font-weight: 600;
    padding: 6px 12px;
    border-radius: 6px;
  }

  .badge.ok {
    background: rgba(120, 220, 150, 0.14);
    color: var(--ok);
    border: 1px solid rgba(120, 220, 150, 0.4);
  }

  .badge.bad {
    background: rgba(235, 110, 110, 0.12);
    color: var(--error);
    border: 1px solid rgba(235, 110, 110, 0.4);
  }
</style>
