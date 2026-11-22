/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Map tiles, e.g. https://tile.openstreetmap.org/{z}/{x}/{y}.png (the default). */
  readonly VITE_MAP_TILE_URL?: string;
  /** Attribution shown on the map for those tiles (HTML, from the build, never from users). */
  readonly VITE_MAP_TILE_ATTRIBUTION?: string;
}
