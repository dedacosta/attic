import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Maven passes the project version; standalone builds fall back to package.json.
// The -SNAPSHOT suffix is a build detail and is not shown on the website.
const version = (process.env.APP_VERSION ?? JSON.parse(readFileSync('package.json', 'utf8')).version).replace(
  /-SNAPSHOT$/,
  '',
)

export default defineConfig({
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(version),
  },
  server: {
    // Listen on all network interfaces so phones on the same Wi-Fi can open the dev server
    host: true,
    // The backend answers everything below /api
    proxy: { '/api': 'http://localhost:8080' },
  },
})
