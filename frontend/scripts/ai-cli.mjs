#!/usr/bin/env node

/**
 * InnTouch AI CLI — 通过 npm scripts 调用 Claude Code Skills
 */

import { spawn } from 'node:child_process'
import { readFileSync, writeFileSync, unlinkSync, mkdtempSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { tmpdir } from 'node:os'

const SKILLS = {
  generate: '/generate',
  inspect: '/inspect',
  'create-component': '/create-component',
  'create-page': '/create-page',
  'update-page': '/update-page',
  analyze: '/analyze',
}

const [skill, ...rest] = process.argv.slice(2)

if (!skill || !SKILLS[skill]) {
  console.error(`
InnTouch AI CLI

Usage: node scripts/ai-cli.mjs <skill> [arguments...]

Available skills:
  generate           Generate services, composables, stores, utils
  inspect            Audit project for issues
  create-component   Create a new Vue component
  create-page        Create a new page view
  update-page        Modify an existing page
  analyze            Analyze code and generate spec

Examples:
  npm run ai:analyze -- src/views/system/menu/index.vue
`)
  process.exit(1)
}

const args = rest.join(' ')

// Read skill definition
const skillFile = resolve(process.cwd(), '.claude', 'commands', `${skill}.md`)
let skillContent = ''
try {
  skillContent = readFileSync(skillFile, 'utf-8')
} catch {
  console.error(`❌ Skill file not found: ${skillFile}`)
  process.exit(1)
}

// Replace $ARGUMENTS and compose prompt
const fullPrompt = skillContent.replace('$ARGUMENTS', args)

// Write prompt to temp file to avoid shell escaping issues
const tmpDir = mkdtempSync(join(tmpdir(), 'inntouch-'))
const tmpFile = join(tmpDir, 'prompt.txt')
writeFileSync(tmpFile, fullPrompt, 'utf-8')

console.log(`\n🚀 Skill: ${skill}`)
console.log(`📎 Args: ${args || '(none)'}`)
console.log(`⏳ Running claude...\n`)

// Use stdin pipe to send prompt
// Windows needs shell:true to resolve .cmd files
const child = spawn('claude', ['-p', '-', '--allowedTools', 'Read,Write,Edit,Glob,Grep,Bash'], {
  cwd: process.cwd(),
  stdio: ['pipe', 'pipe', 'pipe'],
  shell: true,
})

// Feed prompt via stdin
const promptData = readFileSync(tmpFile, 'utf-8')
child.stdin.write(promptData)
child.stdin.end()

let output = ''

child.stdout.on('data', (data) => {
  const text = data.toString()
  output += text
  process.stdout.write(text)
})

child.stderr.on('data', (data) => {
  process.stderr.write(data.toString())
})

child.on('error', (err) => {
  cleanup()
  console.error(`\n❌ Failed to start claude: ${err.message}`)
  process.exit(1)
})

child.on('close', (code) => {
  cleanup()
  if (!output.trim()) {
    console.log('⚠️  No output received. Check that claude CLI is installed and authenticated.')
  }
  console.log(code === 0 ? '\n✅ Done' : `\n❌ Exited with code ${code}`)
  process.exit(code || 0)
})

function cleanup() {
  try { unlinkSync(tmpFile) } catch {}
}
