import { readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const source = process.argv[2]
if (!source) throw new Error('Pass the directory containing the six *_full.txt source files.')
const destination = new URL('../app/src/main/assets/dictionaries/', import.meta.url)
const scripts = {
  fr: /^\p{Script=Latin}[\p{Script=Latin}\p{M}]*(?:'\p{Script=Latin}[\p{Script=Latin}\p{M}]*)*$/u,
  ar: /^\p{Script=Arabic}[\p{Script=Arabic}\p{M}]*$/u,
  hi: /^\p{Script=Devanagari}[\p{Script=Devanagari}\p{M}]*$/u,
}
const letters = /^\p{L}[\p{L}\p{M}]*(?:'\p{L}[\p{L}\p{M}]*)*$/u

for (const language of ['fr', 'de', 'hi', 'ar', 'tr', 'es']) {
  const lines = (await readFile(resolve(source, language + '_full.txt'), 'utf8')).split(/\r?\n/)
  const words = new Set()
  for (const line of lines) {
    const word = line.trim().split(/\s+/, 1)[0].replaceAll('’', "'").toLocaleLowerCase(language).normalize('NFC')
    if (word.length >= 2 && word.length <= 24 && letters.test(word) && (scripts[language] ?? scripts.fr).test(word)) {
      words.add(word)
    }
    if (words.size === 10000) break
  }
  if (words.size !== 10000) throw new Error(language + ' only has ' + words.size + ' usable words')
  await writeFile(new URL(language + '.txt', destination), [...words].join('\n') + '\n')
  console.log(language + ': ' + words.size)
}
