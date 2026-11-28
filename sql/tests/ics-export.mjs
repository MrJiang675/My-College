import {test} from 'node:test'
import assert from 'node:assert/strict'
import {buildCalendar} from '../../Argus-frontend/src/utils/icsExport.ts'

test('calendar escapes injection, folds UTF-8 bytes and converts explicit timezone',()=>{
 const text=buildCalendar([{title:'大学通知'.repeat(25)+',;\\\n下一步',dateTime:'2026-12-10T17:00:00+08:00',location:'办公室,三楼;南侧',description:'第一行\r第二行',needsVerify:false}])
 assert.ok(text.includes('DTSTART:20261210T090000Z\r\n'))
 assert.ok(text.includes('TRIGGER:-P1D\r\n'))
 assert.ok(text.includes('LOCATION:办公室\\,三楼\\;南侧'))
 assert.ok(text.includes('DESCRIPTION:第一行\\n第二行'))
 for(const line of text.split('\r\n'))assert.ok(new TextEncoder().encode(line).length<=75,line)
 assert.equal((text.match(/BEGIN:VEVENT/g)||[]).length,1)
})
test('unknown or impossible dates never export',()=>{
 for(const dateTime of ['','12月10日','2026-02-30','2026-02-30T17:00:00+08:00','2026-12-10T17:00:00'])
  assert.throws(()=>buildCalendar([{title:'待核实',dateTime,needsVerify:false}]))
 assert.throws(()=>buildCalendar([{title:'未确认',dateTime:'2026-12-10',needsVerify:true}]))
})
test('multiple confirmed events use all-day or UTC fields independently',()=>{
 const text=buildCalendar([{title:'报名',dateTime:'2026-12-10',needsVerify:false},{title:'初审',dateTime:'2026-12-09T09:00:00+08:00',needsVerify:false}])
 assert.equal((text.match(/BEGIN:VEVENT/g)||[]).length,2)
 assert.ok(text.includes('DTSTART;VALUE=DATE:20261210'))
 assert.ok(text.endsWith('END:VCALENDAR\r\n'))
})
