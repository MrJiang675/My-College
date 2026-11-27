import {readFileSync} from 'node:fs'
import {demoSamples} from '../../Argus-frontend/src/constants/demoSamples.ts'
import {test} from 'node:test'
import assert from 'node:assert/strict'
test('all preset citations are exact sample excerpts',()=>{
 for(const sample of demoSamples){
  const source=readFileSync(new URL('../../Argus-frontend/public'+sample.filePath,import.meta.url),'utf8')
  for(const preset of sample.presets)for(const citation of preset.citations)assert.ok(source.includes(citation.chunkExcerpt),sample.id)
 }
})
