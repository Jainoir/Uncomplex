import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { writeFile } from 'node:fs/promises'
import { performance } from 'node:perf_hooks'
import { join } from 'node:path'

// Run from the repository root after packaging the backend. Uses only H2 and mock AI.
const java = process.env.BENCHMARK_JAVA ?? (process.env.JAVA_HOME
  ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java')
const results = []

async function run(label, lazy, overrides = [], guard) {
  const startedAt = performance.now()
  let output = ''
  let startupMs
  let started
  const ready = new Promise(resolve => { started = resolve })
  const options = [
    '--spring.profiles.active=local', '--server.address=127.0.0.1', '--server.port=0',
    '--spring.datasource.url=jdbc:h2:mem:startup-benchmark;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH',
    '--spring.datasource.username=sa', '--spring.datasource.password=',
    '--spring.datasource.driver-class-name=org.h2.Driver', '--app.ai.provider=mock',
    '--app.link-health.enabled=false', '--app.rate-limit.store=memory',
    '--app.rate-limit.require-trusted-proxy=true', '--app.rate-limit.trusted-proxy-hops=1',
    '--app.rate-limit.trusted-proxy-cidrs=',
    '--app.security.jwt-secret=benchmark-local-secret-0123456789abcdef',
    '--app.cors.allowed-origins=https://uncomplex.vercel.app',
    '--debug=false', '--logging.level.root=INFO', '--management.health.redis.enabled=false',
    `--spring.main.lazy-initialization=${lazy}`, ...overrides,
  ]
  // Spring combines repeated command-line keys, so replace rather than append overrides.
  const uniqueOptions = new Map(options.map(option => [option.slice(0, option.indexOf('=')), option]))
  const child = spawn(java, [
    '-Xmx384m', '-jar', 'target/uncomplex-api-0.1.0-SNAPSHOT.jar', ...uniqueOptions.values(),
  ], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
  const exited = once(child, 'exit')
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {
    output += chunk.toString()
    if (startupMs === undefined && output.includes('Started UncomplexApplication')) {
      startupMs = performance.now() - startedAt
      started()
    }
  })
  let timer
  try {
    const outcome = await Promise.race([
      ready.then(() => 'ready'), exited.then(() => 'exit'),
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('Startup exceeded 60 seconds')), 60_000) }),
    ])
    if (guard) {
      const result = { label, lazy, failedBeforeReady: outcome === 'exit' && child.exitCode !== 0,
        expectedError: output.includes(guard) }
      results.push(result)
      console.log(JSON.stringify(result))
      if (!result.failedBeforeReady || !result.expectedError) process.exitCode = 1
      return
    }
    if (outcome !== 'ready') throw new Error(`${label} exited before startup: ${output.slice(-4000)}`)
    const port = output.match(/Tomcat started on port (\d+)/)?.[1]
    if (!port) throw new Error('No listening port in startup log')
    const base = `http://127.0.0.1:${port}`
    const response = await fetch(base + '/actuator/health', {
      headers: { Origin: 'https://uncomplex.vercel.app' }, signal: AbortSignal.timeout(30_000),
    })
    if (response.status !== 200 || (await response.json()).status !== 'UP') throw new Error('Health failed')
    const healthMs = performance.now() - startedAt
    const generated = await fetch(base + '/api/roadmaps', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ topic: 'Startup benchmark', context: '', experienceLevel: 'BEGINNER', goal: 'GENERAL_UNDERSTANDING' }),
      signal: AbortSignal.timeout(30_000),
    })
    const roadmap = await generated.json()
    if (generated.status !== 201 || !roadmap.shareToken || !roadmap.prerequisites?.length) {
      throw new Error(`Generation failed: ${generated.status} ${JSON.stringify(roadmap)}`)
    }
    const firstRoadmapMs = performance.now() - startedAt
    const shared = await fetch(base + '/api/roadmaps/public/' + roadmap.shareToken)
    if (shared.status !== 200 || (await shared.json()).shareToken !== roadmap.shareToken) throw new Error('Shared read failed')
    const result = { label, lazy, startupMs: Math.round(startupMs), healthMs: Math.round(healthMs),
      firstRoadmapMs: Math.round(firstRoadmapMs), healthCors: response.headers.get('access-control-allow-origin') }
    results.push(result)
    console.log(JSON.stringify(result))
  } finally {
    clearTimeout(timer)
    if (child.exitCode === null) child.kill()
    await exited
    await writeFile(`target/startup-${label}.log`, output)
  }
}

if (process.argv.includes('--guards')) {
  for (const lazy of [false, true]) {
    await run(`proxy-${lazy}`, lazy, ['--app.rate-limit.trusted-proxy-hops=0'], 'Set TRUSTED_PROXY_HOPS')
    await run(`jwt-${lazy}`, lazy, ['--app.security.jwt-secret=short'], 'JWT_SECRET must be at least 32 bytes')
    await run(`ai-${lazy}`, lazy, ['--app.ai.provider=anthropic', '--app.ai.anthropic.api-key='], 'requires the ANTHROPIC_API_KEY')
  }
} else {
  for (const [i, lazy] of [false, true, true, false, false, true].entries()) await run(`timing-${i + 1}`, lazy)
  for (const lazy of [false, true]) {
    const runs = results.filter(r => r.lazy === lazy)
    const median = key => runs.map(r => r[key]).sort((a, b) => a - b)[1]
    console.log(JSON.stringify({ lazy, medianStartupMs: median('startupMs'), medianHealthMs: median('healthMs'),
      medianFirstRoadmapMs: median('firstRoadmapMs') }))
  }
}
await writeFile(`target/startup-${process.argv.includes('--guards') ? 'guards' : 'timings'}.json`, JSON.stringify(results, null, 2) + '\n')
