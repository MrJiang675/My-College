package com.argus.rag.college;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ModuleSkillPromptRegistry {
    private final Map<String,String> templates;
    public ModuleSkillPromptRegistry() {
        var names = Map.of("LEARNING","learning","PRACTICE","practice","CAMPUS_NOTICE","campus_notice",
                "GROUP_PROJECTS","group_projects","EXPERIENCE","experience","CAREER","career","GENERAL","general");
        var loaded = new java.util.HashMap<String,String>();
        names.forEach((code,file)->{
            try(var input=new ClassPathResource("prompts/skills/"+file+".st").getInputStream()){
                loaded.put(code,new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }catch(java.io.IOException e){throw new IllegalStateException("无法加载模块提示 "+file,e);}
        });
        templates=Map.copyOf(loaded);
    }
    public String render(ModuleContext context) {
        String code=context==null||context.moduleCode()==null?"GENERAL":context.moduleCode();
        String prompt=templates.getOrDefault(code,templates.get("GENERAL"));
        if(context==null||Boolean.FALSE.equals(context.enableProfileAdaptation()))return prompt+"\n本次按跨专业任务处理。";
        CampusProfile profile=context.campusProfile();
        return prompt+"\n校园档案（仅用于解释适配，不构成校规证据）：学校="+safe(profile==null?null:profile.school())+
                "，学院="+safe(profile==null?null:profile.college())+"，专业="+safe(profile==null?null:profile.major())+
                "，年级="+safe(profile==null?null:profile.grade())+"。视角="+safe(context.subMode());
    }
    private String safe(String value){return value==null||value.isBlank()?"未填写":value.strip().substring(0,Math.min(value.strip().length(),120));}
}
