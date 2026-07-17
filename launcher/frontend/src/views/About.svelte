<script lang="ts">
  import { onMount } from 'svelte'
  import logo from '../assets/logo.png'
  import { appVersion, openGitHub } from '../lib/bridge'

  let version = ''
  onMount(async () => {
    try {
      version = await appVersion()
    } catch {
      version = ''
    }
  })
</script>

<div class="page">
  <div class="hero">
    <img src={logo} alt="Esdeath: Cryostasis" />
    <div>
      <h1>Esdeath: Cryostasis</h1>
      <p class="tag">A modern Fabric client and its launcher.</p>
      {#if version}<p class="ver">Launcher v{version}</p>{/if}
    </div>
  </div>

  <section class="card">
    <p>
      This launcher installs and updates the mod, and lets you choose which backend the client
      talks to. The backend is open source: you can run your own instance and point the client at
      it from Settings, or use the hosted default.
    </p>
    <button class="repo" on:click={openGitHub}>View the project on GitHub</button>
  </section>

  <section class="card muted">
    <p>
      Everything the launcher writes goes into your existing Minecraft install: Fabric, Fabric API,
      the mod jar, and a launch profile named "Esdeath Cryostasis". No Minecraft files are bundled
      or redistributed, and you launch through the official Minecraft launcher.
    </p>
  </section>
</div>

<style>
  .page {
    padding: 34px;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .hero {
    display: flex;
    align-items: center;
    gap: 20px;
  }

  .hero img {
    width: 76px;
    height: 76px;
    object-fit: contain;
    filter: drop-shadow(0 0 10px rgba(90, 143, 199, 0.5));
  }

  h1 {
    margin: 0;
    font-size: 26px;
    letter-spacing: 0.5px;
  }

  .tag {
    margin: 6px 0 0;
    color: var(--subtext);
    font-size: 14px;
  }

  .ver {
    margin: 4px 0 0;
    color: var(--accent);
    font-size: 13px;
    font-weight: 600;
  }

  .card {
    background: var(--panel);
    border: 1px solid rgba(90, 143, 199, 0.12);
    border-radius: var(--radius);
    padding: 20px 22px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .card p {
    margin: 0;
    line-height: 1.6;
    font-size: 14px;
  }

  .card.muted p {
    color: var(--subtext);
    font-size: 13px;
  }

  .repo {
    align-self: flex-start;
    background: var(--accent);
    color: #06111d;
    font-weight: 700;
    font-size: 14px;
    padding: 11px 24px;
    border-radius: var(--radius);
    transition: filter 0.12s;
  }

  .repo:hover {
    filter: brightness(1.08);
  }
</style>
