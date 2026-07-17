<script lang="ts">
  import { onMount, onDestroy } from 'svelte'
  import {
    getStatus,
    install,
    play,
    openMinecraftFolder,
    onProgress,
    type Status,
    type LogLine,
  } from '../lib/bridge'

  let status: Status | null = null
  let log: LogLine[] = []
  let running = false
  let unsubscribe: (() => void) | null = null
  let logEl: HTMLDivElement

  onMount(() => {
    unsubscribe = onProgress((line) => {
      log = [...log, line]
      // Follow the tail as new lines arrive, so the current step is always in view.
      queueMicrotask(() => logEl?.scrollTo({ top: logEl.scrollHeight }))
    })
    void refresh()
  })

  onDestroy(() => unsubscribe?.())

  async function refresh() {
    try {
      status = await getStatus()
    } catch {
      status = null
    }
  }

  async function run(action: () => Promise<unknown>) {
    if (running) return
    running = true
    log = []
    try {
      await action()
    } catch (err) {
      // The engine already emitted an error line; this catch just stops the spinner and keeps
      // the promise rejection from surfacing as an unhandled error.
    } finally {
      running = false
      await refresh()
    }
  }

  const onPlay = () => run(play)
  const onInstall = () => run(install)

  $: primaryLabel = running
    ? 'Working...'
    : status?.updateAvailable && status?.installedVersion
      ? 'Update and Play'
      : 'Play'
</script>

<div class="page">
  <header>
    <h1>Play</h1>
    <p class="lede">Install or update the client, then launch Minecraft with your chosen backend.</p>
  </header>

  <section class="status">
    {#if status && !status.minecraftFound}
      <div class="banner problem">
        <strong>Minecraft was not found.</strong>
        <span>{status.problem}</span>
      </div>
    {:else if status}
      <div class="grid">
        <div class="stat">
          <span class="key">Installed</span>
          <span class="val">{status.installedVersion || 'not installed'}</span>
        </div>
        <div class="stat">
          <span class="key">Latest</span>
          <span class="val">{status.latestVersion || 'unknown'}</span>
        </div>
        <div class="stat">
          <span class="key">Fabric</span>
          <span class="val">{status.fabricVersion || 'not installed'}</span>
        </div>
        <div class="stat">
          <span class="key">Backend</span>
          <span class="val mono">{status.backendUrl}</span>
        </div>
      </div>
      {#if status.updateAvailable}
        <div class="banner update">
          {status.installedVersion
            ? `Version ${status.latestVersion} is available.`
            : 'The client is not installed yet.'}
        </div>
      {/if}
    {:else}
      <div class="banner">Reading install state...</div>
    {/if}
  </section>

  <section class="actions">
    <button class="primary" on:click={onPlay} disabled={running}>{primaryLabel}</button>
    <button class="ghost" on:click={onInstall} disabled={running}>Install / Update only</button>
    <button class="ghost" on:click={openMinecraftFolder} disabled={running}>Open folder</button>
  </section>

  {#if log.length}
    <section class="console" bind:this={logEl}>
      {#each log as line}
        <div class="line {line.level}">{line.text}</div>
      {/each}
    </section>
  {/if}
</div>

<style>
  .page {
    padding: 30px 34px;
    display: flex;
    flex-direction: column;
    gap: 22px;
    min-height: 100%;
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

  .status {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .stat {
    background: var(--panel);
    border: 1px solid rgba(90, 143, 199, 0.12);
    border-radius: var(--radius);
    padding: 14px 16px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .key {
    color: var(--subtext);
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 1px;
  }

  .val {
    font-size: 15px;
    font-weight: 600;
    overflow-wrap: anywhere;
  }

  .mono {
    font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
    font-size: 13px;
    font-weight: 500;
  }

  .banner {
    background: var(--panel);
    border: 1px solid rgba(90, 143, 199, 0.14);
    border-radius: var(--radius);
    padding: 12px 16px;
    font-size: 14px;
    color: var(--subtext);
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .banner.update {
    border-color: var(--accent);
    color: var(--text);
  }

  .banner.problem {
    border-color: var(--warn);
    color: var(--text);
  }

  .actions {
    display: flex;
    gap: 12px;
    align-items: center;
    flex-wrap: wrap;
  }

  .primary {
    background: var(--accent);
    color: #06111d;
    font-weight: 700;
    font-size: 15px;
    padding: 13px 40px;
    border-radius: var(--radius);
    transition: filter 0.12s, transform 0.05s;
  }

  .primary:hover:not(:disabled) {
    filter: brightness(1.08);
  }

  .primary:active:not(:disabled) {
    transform: translateY(1px);
  }

  .ghost {
    background: var(--row);
    color: var(--text);
    font-weight: 600;
    font-size: 14px;
    padding: 12px 20px;
    border-radius: var(--radius);
    border: 1px solid rgba(90, 143, 199, 0.16);
    transition: background 0.12s;
  }

  .ghost:hover:not(:disabled) {
    background: var(--row-hover);
  }

  button:disabled {
    opacity: 0.55;
    cursor: default;
  }

  .console {
    background: var(--cell);
    border: 1px solid var(--cell-border);
    border-radius: var(--radius);
    padding: 14px 16px;
    font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
    font-size: 12.5px;
    line-height: 1.7;
    overflow-y: auto;
    max-height: 260px;
    flex: 1;
    user-select: text;
  }

  .line {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .line.step {
    color: var(--accent);
    font-weight: 700;
    margin-top: 6px;
  }
  .line.ok {
    color: var(--ok);
  }
  .line.info {
    color: var(--subtext);
    padding-left: 12px;
  }
  .line.warn {
    color: var(--warn);
  }
  .line.error {
    color: var(--error);
    font-weight: 600;
  }
  .line.done {
    color: var(--ok);
    font-weight: 700;
    margin-top: 6px;
  }
</style>
