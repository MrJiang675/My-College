import {test} from 'node:test'
import assert from 'node:assert/strict'
import {experimentPlugins,valueAt,validModel,normalCdf} from '../../Argus-frontend/src/views/college/models.ts'
test('RC and beam numerical regressions',()=>{
 assert.equal(experimentPlugins.rc.compute([12,50,6]).metrics.时间常数,0.6)
 assert.ok(Math.abs(valueAt({kind:'rc',values:[12,50,6]},0.2)-6*(1-Math.exp(-1)))<1e-12)
 assert.ok(Math.abs(valueAt({kind:'beam',values:[1,20,40,50,200,1]},1)-0.78125)<1e-12)
})
test('normal sampling and validation',()=>{
 const r=experimentPlugins.normal.compute([3,2,100])
 assert.equal(r.metrics.标准误,0.2)
 assert.ok(Math.abs(r.metrics.置信下界-2.608)<1e-12)
 assert.ok(Math.abs(normalCdf(0)-0.5)<1e-6)
 assert.equal(validModel({kind:'normal',values:[-2,1,25]}),true)
 assert.equal(validModel({kind:'normal',values:[0,0,25]}),false)
 assert.equal(validModel({kind:'normal',values:[0,1,2.5]}),false)
})
