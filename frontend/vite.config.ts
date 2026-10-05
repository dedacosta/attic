import { readFileSync } from 'node:fs'
import { defineConfig, version as viteVersion } from 'vite'
import react from '@vitejs/plugin-react'

// Maven passes the project version; standalone builds fall back to package.json.
// The -SNAPSHOT suffix is a build detail and is not shown on the website.
const version = (process.env.APP_VERSION ?? JSON.parse(readFileSync('package.json', 'utf8')).version).replace(
  /-SNAPSHOT$/,
  '',
)

/** The version of an installed package, e.g. 19.3.0 */
function installed(name: string): string {
  return JSON.parse(readFileSync(`node_modules/${name}/package.json`, 'utf8')).version
}

/** The Spring Boot version, from the parent of the Maven project */
function springBootVersion(): string {
  const pom = readFileSync('../pom.xml', 'utf8')
  const match = /<artifactId>spring-boot-starter-parent<\/artifactId>\s*<version>([^<]+)<\/version>/.exec(pom)
  if (!match) {
    throw new Error('Spring Boot version not found in pom.xml')
  }
  return match[1]
}

// What the app is built with, for the footer
const builtWith = [
  'Anthropic Opus 5.5',
  `Spring Boot v${springBootVersion()}`,
  `React v${installed('react')}`,
  `Vite v${viteVersion}`,
  `jsPDF v${installed('jspdf')}`,
].join(' · ')

export default defineConfig({
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(version),
    __BUILT_WITH__: JSON.stringify(builtWith),
  },
  server: {
    // Listen on all network interfaces so phones on the same Wi-Fi can open the dev server
    host: true,
    // The backend answers everything below /api
    proxy: { '/api': 'http://localhost:8080' },
  },
})
