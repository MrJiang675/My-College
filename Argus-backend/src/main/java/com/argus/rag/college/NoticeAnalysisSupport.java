package com.argus.rag.college;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.Pattern;

/** Evidence checks are deterministic. A plausible model statement is not eligibility evidence. */
public final class NoticeAnalysisSupport {
    private final ObjectMapper json;
    public NoticeAnalysisSupport(ObjectMapper json){this.json=json;}
    public static final String SCHEMA="""
        只返回一个 JSON 对象，保持 answer 和 quotes；并完整返回下列结构：
        {"answer":"基于原文的分析","quotes":["原句"],
        "eligibility":{"status":"YELLOW","summary":"结合学院与年级说明符合、不符合或缺什么","evidences":["原句"]},
        "opportunity":{"level":"原文明示活动级别或需核实","audience":"面向对象","benefit":"原文明示收获或需核实","credit":"综测加分：缺本院认定目录则需核实","risks":["核心风险"]},
        "missingDocuments":["原文未附的院级文件或待核实事项"],
        "teacherInquiryScripts":["老师您好，关于……想请教……谢谢老师。"],
        "deadlines":[{"title":"事项","dateTime":"2026-12-10T17:00:00+08:00","location":"原文地点或空","description":"原文要求","needsVerify":true,"evidenceQuote":"含时间的原句"}],
        "checklist":[{"item":"材料名称","required":true,"formatNote":"原文格式说明或需核实","evidenceQuote":"原句"}]}
        上述日期仅是结构示意，绝对不能作为用户通知的日期。日期不全则 dateTime 为空并 needsVerify=true。
        不得猜测当前年份、内网规则、隐性加分或缺失附件的具体内容。GREEN 仅表示明示资格符合，不代表综测必加分。
        红绿灯必须有用户原文的逐字依据；档案缺失或未覆盖全部资格要求时给 YELLOW。关闭档案适配也给 YELLOW。
        """;

    public ObjectNode normalize(JsonNode input,String source,CampusProfile profile,Boolean adaptation) {
        ObjectNode out=input instanceof ObjectNode object?object.deepCopy():json.createObjectNode();
        if(!out.path("answer").isTextual())out.put("answer","【需核实】通知结构解析不完整，请核对原文后重试。");
        var eligibility=json.createObjectNode();
        var evidence=eligibility.putArray("evidences");
        for(var q:out.path("eligibility").path("evidences"))if(q.isTextual()&&!q.asText().isBlank()&&matchesEvidence(source,q.asText()))evidence.add(originalEvidence(source,q.asText()));
        String status=out.path("eligibility").path("status").asText("YELLOW");
        boolean hasProfile=profile!=null&&profile.college()!=null&&!profile.college().isBlank()&&profile.grade()!=null&&!profile.grade().isBlank();
        if(!Set.of("GREEN","YELLOW","RED").contains(status)||evidence.isEmpty()||!hasProfile||Boolean.FALSE.equals(adaptation))status="YELLOW";
        String summary=out.path("eligibility").path("summary").asText("资格条件需对照原文和学院口径核实。");
        if(!hasProfile||Boolean.FALSE.equals(adaptation))summary="未启用完整学院/年级档案，暂不能作出个人资格裁决。";
        else if(evidence.isEmpty())summary=profile.college()+" · "+profile.grade()+"：缺少可逐字核对的资格依据，请向老师核实。";
        if(hasProfile&&!Boolean.FALSE.equals(adaptation)&&!evidence.isEmpty()) {
            String mismatch=explicitMismatch(evidence,profile);
            if(mismatch!=null){status="RED";summary=profile.college()+" · "+profile.grade()+"："+mismatch;}
            else if(evidence.toString().contains("学院")&&!evidence.toString().contains(profile.college())&&!evidence.toString().contains("各学院")&&!evidence.toString().contains("不限学院")){status="YELLOW";summary="学院名称可能存在简称或范围差异，请向老师核实，不按简称直接判定不符合。";}
        }
        eligibility.put("status",status).put("summary",summary);out.set("eligibility",eligibility);
        var missing=json.createArrayNode();
        for(var item:out.path("missingDocuments"))if(item.isTextual()&&!item.asText().isBlank()&&missing.size()<12)missing.add(item.asText().substring(0,Math.min(500,item.asText().length())));
        for(var item:out.path("needsVerification"))if(item.isTextual()&&!missing.toString().contains(item.asText())&&
            (!item.asText().contains("综测")||source.matches("(?s).*(综测|竞赛|评奖|奖学金|推免|保研).*")))missing.add(item.asText());
        if(missing.isEmpty()&&!source.matches("(?s).*(学院|本院).*(认定目录|测评细则).*"))missing.add("学院当期认定文件是否适用，需向辅导员核实");
        out.set("missingDocuments",missing);
        var scripts=json.createArrayNode();
        for(var item:out.path("teacherInquiryScripts"))if(item.isTextual()&&!item.asText().isBlank()&&scripts.size()<2)scripts.add(item.asText().substring(0,Math.min(1000,item.asText().length())));
        if(scripts.isEmpty())scripts.add("老师您好，我想核实这份通知的适用范围。请问本院本年级是否可以报名，相关认定文件和院内截止时间应以哪一份通知为准？谢谢老师！");
        out.set("teacherInquiryScripts",scripts);
        var deadlines=json.createArrayNode();
        for(var d:out.path("deadlines"))if(d.isObject()&&deadlines.size()<12) {
            var copy=((ObjectNode)d).deepCopy();String date=d.path("dateTime").asText("");
            boolean verified=hasDateEvidence(source,date,d.path("evidenceQuote").asText(""));
            copy.put("needsVerify",d.path("needsVerify").asBoolean(true)||!verified);
            copy.put("dateTime",date);deadlines.add(copy);
        }
        out.set("deadlines",deadlines);
        var checklist=json.createArrayNode();
        for(var c:out.path("checklist"))if(c.isObject()&&checklist.size()<20) {
            String quote=c.path("evidenceQuote").asText("");
            if(!quote.isBlank()&&matchesEvidence(source,quote)&&c.path("item").isTextual()){var copy=((ObjectNode)c).deepCopy();copy.put("evidenceQuote",originalEvidence(source,quote));checklist.add(copy);}
        }
        out.set("checklist",checklist);
        var attachments=out.putArray("attachmentWarnings");
        for(String paragraph:source.split("\\n"))if(paragraph.matches(".*(?:见附件|详见附件|附件[0-9一二三四]|另附表格).*"))attachments.add(paragraph);
        return out;
    }
    static String normalized(String text){return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}]+","").replace('，',',').replace('。','.').replace('；',';').replace('：',':');}
    static boolean matchesEvidence(String source,String quote){return !quote.isBlank()&&normalized(source).contains(normalized(quote));}
    static String originalEvidence(String source,String quote){if(source.contains(quote))return quote;for(String paragraph:source.split("\\n\\s*\\n"))if(matchesEvidence(paragraph,quote))return paragraph;return source;}
    private String explicitMismatch(ArrayNode evidence,CampusProfile profile) {
        for(var q:evidence) {
            String quote=q.asText();
            if(!quote.matches("(?s).*(仅限|仅面向|申请对象为|报送对象为).*"))continue;

            var grades=Pattern.compile("大([一二三四])").matcher(quote);var allowed=new TreeSet<Integer>();
            while(grades.find())allowed.add("一二三四".indexOf(grades.group(1))+1);
            int own=List.of("大一","大二","大三","大四").indexOf(profile.grade())+1;
            if(!allowed.isEmpty()&&own>0) {
                if(quote.matches("(?s).*大[一二三四](?:及)?以上.*"))for(int i=allowed.first();i<=4;i++)allowed.add(i);
                if(quote.matches("(?s).*大[一二三四](?:至|到|[-—])大[一二三四].*")){int last=allowed.last();for(int i=allowed.first();i<=last;i++)allowed.add(i);}
                if(!allowed.contains(own))return "原文明示年级范围不包含当前年级，请勿按符合资格直接报名。";
            }
        }
        return null;
    }

    private boolean hasDateEvidence(String source,String date,String quote) {
        if(quote.isBlank()||!matchesEvidence(source,quote)||!date.matches("\\d{4}-\\d{2}-\\d{2}(?:T.*)?"))return false;
        try {
            var day=java.time.LocalDate.parse(date.substring(0,10));
            String pattern=day.getYear()+"(?:年|[-/])0?"+day.getMonthValue()+"(?:月|[-/])0?"+day.getDayOfMonth()+"(?:日|\\b)";
            if(!Pattern.compile(pattern).matcher(quote).find())return false;
            if(date.length()>10) {
                var time=java.time.OffsetDateTime.parse(date);
                String clock="0?"+time.getHour()+"(?:[:：时])0?"+time.getMinute()+"(?:分|\\b)";
                return time.getOffset().equals(java.time.ZoneOffset.ofHours(8))&&Pattern.compile(clock).matcher(quote).find();
            }
            return true;
        }catch(Exception invalid){return false;}
    }
}
