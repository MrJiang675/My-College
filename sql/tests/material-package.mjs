import {test} from 'node:test'
import assert from 'node:assert/strict'
import {makeZip,crc32,safeFileName} from '../../Argus-frontend/src/utils/zipPackager.ts'
import {demoXlsx,demoPdf,generateDemoMaterials} from '../../Argus-frontend/src/utils/demoMaterialGenerator.ts'
import {newRequirement,blockers,matchesEvidence} from '../../Argus-frontend/src/utils/noticeMaterials.ts'
function names(bytes){let i=0;const names=[];const v=new DataView(bytes.buffer);while(v.getUint32(i,true)===0x04034b50){const size=v.getUint32(i+18,true),n=v.getUint16(i+26,true),extra=v.getUint16(i+28,true);const data=bytes.slice(i+30+n+extra,i+30+n+extra+size);assert.equal(crc32(data),v.getUint32(i+14,true));names.push(new TextDecoder().decode(bytes.slice(i+30,i+30+n)));i+=30+n+extra+size}return names}
test('ZIP CRC, UTF8, safe paths, duplicate rejection, real OOXML package',async()=>{
 assert.equal(crc32(new TextEncoder().encode('123456789')),0xcbf43926)
 const z=await makeZip([{name:'清单.md',data:'你好'}]);assert.deepEqual(names(new Uint8Array(await z.arrayBuffer())),['清单.md'])
 assert.ok(!safeFileName('../../secret').includes('/'))
 await assert.rejects(makeZip([{name:'x',data:''},{name:'x',data:''}]))
 await assert.rejects(makeZip([{name:'../x',data:''}],true))
 const x=names(new Uint8Array(await(await demoXlsx()).arrayBuffer()));assert.ok(x.includes('_rels/.rels'));assert.ok(x.includes('xl/worksheets/sheet1.xml'))
 assert.ok((await demoPdf().text()).startsWith('%PDF-1.4'));assert.equal((await generateDemoMaterials()).length,4)
})
test('material format cannot be fixed by renaming; manual checks gate pack',()=>{
 const r=newRequirement('申请书','PDF 本人签字'),f=new File(['x'],'申请书.csv'),files=new Map([['id',f]]);r.fileId='id';r.outputName='申请书.pdf'
 assert.ok(blockers(r,files).includes('文件扩展名与要求不符'));assert.ok(blockers(r,files).some(s=>s.includes('伪装')))
 files.set('id',new File(['%PDF'],'申请书.pdf'));r.copies=1;r.copiesChecked=r.contentChecked=r.signatureChecked=true;assert.deepEqual(blockers(r,files),[])
 assert.ok(blockers(r,new Map()).length);assert.ok(matchesEvidence('提交申请表，\n 盖章；','提交申请表,盖章;'))
})

test('explicit filename template detects bad name and accepts corrected local rename',()=>{const r=newRequirement('报名表','PDF，命名为“学号_姓名_报名表.pdf”'),files=new Map([['f',new File(['x'],'draft.pdf')]]);r.fileId='f';r.outputName='draft.pdf';assert.ok(blockers(r,files).some(s=>s.includes('文件名不合规范')));r.outputName='20261234_张三_报名表.pdf';r.namingChecked=r.contentChecked=r.copiesChecked=true;r.copies=1;assert.deepEqual(blockers(r,files),[])})
