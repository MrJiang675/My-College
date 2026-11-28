import {test} from 'node:test'
import assert from 'node:assert/strict'
import {compileFormula} from '../../Argus-frontend/src/utils/formulaEvaluator.ts'
import {experimentPlugins,defaultModel,validModel,formulaResult} from '../../Argus-frontend/src/views/college/models.ts'
test('seven models finite, projectile analytic range, binomial mass',()=>{
 assert.equal(Object.keys(experimentPlugins).length,7)
 for(const k of Object.keys(experimentPlugins))assert.ok(validModel(defaultModel(k)),k)
 const projectile=experimentPlugins.projectile.compute([20,45,0,10]);assert.ok(Math.abs(projectile.metrics.射程-40)<1e-10)
 const b=experimentPlugins.binomial.compute([200,.5]);assert.ok(Math.abs(b.points.reduce((s,p)=>s+p.y,0)-1)<1e-10)
 for(const p of [0,1])assert.equal(experimentPlugins.binomial.compute([20,p]).points.reduce((s,p)=>s+p.y,0),1)
 assert.equal(validModel({kind:'binomial',values:[2.5,.5]}),false)
})
test('formula grammar, precedence, all whitelist functions, injection and singularities',()=>{
 assert.equal(compileFormula('-2^2 + 2^3^2',[] )({}),508)
 assert.equal(compileFormula('pow(2,3)+min(1,2)+max(1,2)',[])({}),11)
 assert.equal(compileFormula('sin(pi/2)+ln(e)',[])({}),2)
 for(const s of ['x.constructor','alert(1)','x=2','globalThis','sin(x);1','[1]','pow(2)','1e999','sin'.repeat(200)])assert.throws(()=>compileFormula(s,['x']))
 const m=defaultModel('formula');assert.ok(validModel(m));assert.equal(formulaResult(m).points.length,401)
 assert.equal(validModel({...m,expression:'1/x',xMin:-1,xMax:1}),false)
 assert.equal(validModel({...m,expression:'sqrt(-1)'}),false)
})
