// Typed bridge over the globals Wails injects (window.go for bound Go methods, window.runtime
// for events). It is hand-written rather than generated so the frontend builds without running
// `wails generate`; every shape here mirrors a DTO in launcher/app.go one to one, so a change
// on the Go side that is not reflected here is a compile error waiting in the view that uses it.
//
// The globals are read lazily inside each call because Wails injects them before the app runs
// but not before this module is imported; reading them at call time avoids a load-order trap.

export interface Config {
  schemaVersion: number
  backendUrl: string
  minecraftDir: string
  minecraftVersion: string
}

export interface BackendStatus {
  ok: boolean
  version: string
  message: string
}

export interface Status {
  minecraftDir: string
  minecraftFound: boolean
  fabricVersion: string
  installedVersion: string
  latestVersion: string
  updateAvailable: boolean
  backendUrl: string
  problem: string
}

export interface PlayResult {
  ready: boolean
  launcherOpened: boolean
  profileName: string
}

export interface LogLine {
  level: string
  text: string
}

interface GoApp {
  AppVersion(): Promise<string>
  GetConfig(): Promise<Config>
  SaveConfig(cfg: Config): Promise<Config>
  TestBackend(url: string): Promise<BackendStatus>
  GetStatus(): Promise<Status>
  Install(): Promise<void>
  Play(): Promise<PlayResult>
  OpenGitHub(): Promise<void>
  OpenMinecraftFolder(): Promise<void>
  SelectMinecraftDir(): Promise<string>
}

interface WailsRuntime {
  EventsOn(event: string, cb: (...data: any[]) => void): () => void
}

declare global {
  interface Window {
    go: { main: { App: GoApp } }
    runtime: WailsRuntime
  }
}

const app = (): GoApp => window.go.main.App

export const appVersion = () => app().AppVersion()
export const getConfig = () => app().GetConfig()
export const saveConfig = (cfg: Config) => app().SaveConfig(cfg)
export const testBackend = (url: string) => app().TestBackend(url)
export const getStatus = () => app().GetStatus()
export const install = () => app().Install()
export const play = () => app().Play()
export const openGitHub = () => app().OpenGitHub()
export const openMinecraftFolder = () => app().OpenMinecraftFolder()
export const selectMinecraftDir = () => app().SelectMinecraftDir()

const PROGRESS_EVENT = 'engine:progress'

// onProgress subscribes to the install log stream and returns an unsubscribe function, so a
// view can listen while mounted and drop the listener when it leaves.
export function onProgress(cb: (line: LogLine) => void): () => void {
  return window.runtime.EventsOn(PROGRESS_EVENT, (line: LogLine) => cb(line))
}
