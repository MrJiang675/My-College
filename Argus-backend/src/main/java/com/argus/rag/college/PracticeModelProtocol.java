package com.argus.rag.college;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Wire protocol only. Browser independently validates ranges and samples every formula. */
public final class PracticeModelProtocol {
    private PracticeModelProtocol() {}
    public static final Map<String,double[]> DEFAULTS=Map.of(
        "rc",new double[]{12,50,6}, "beam",new double[]{1,20,40,50,200,1},
        "normal",new double[]{0,1,25}, "damped",new double[]{1,.15,3},
        "projectile",new double[]{20,45,0,9.81}, "power",new double[]{12,10,10}, "binomial",new double[]{20,.5});
    public static final String INSTRUCTIONS="""
        必须返回 JSON 对象 answer 和 model，不输出程序。内置模型数值数组顺序：
        rc=[R千欧,C微法,U伏] 默认[12,50,6]；beam=[L米,b毫米,h毫米,F牛,E吉帕,加载位置比例] 默认[1,20,40,50,200,1]；
        normal=[均值,标准差,样本量整数] 默认[0,1,25]；damped=[振幅,阻尼比(0到1之间),固有角频率] 默认[1,0.15,3]；
        projectile=[初速度,仰角度数(0到90之间),初始高度,重力] 默认[20,45,0,9.81]；
        power=[电动势,内阻,负载电阻] 默认[12,10,10]；binomial=[试验次数整数,成功概率] 默认[20,0.5]。
        当用户上传材料并要求模拟/画曲线，模型明确但次要参数不全时禁止 model:null，使用上述默认值补齐且在 answer 开头明确告知：
        材料未给全参数，已按标准默认条件启动模拟实验，可直接在右侧滑块修改为你的实测条件。
        已明确给出的参数必须保留，冲突/非适用物理条件先核实，不强行套模型。有当前模型时仅改变用户指定量。
        不属于七套但有明确函数关系时输出 model={kind:"formula",title:"图名",discipline:"学科",summary:"假设",expression:"A * exp(-k * x) * cos(w * x)",xVar:"x",xLabel:"时间/s",yLabel:"振幅/m",xMin:0,xMax:10,params:[{name:"振幅",symbol:"A",unit:"m",value:1,min:0,max:10,step:0.1},{name:"衰减系数",symbol:"k",value:0.2,min:0,max:2,step:0.01},{name:"角频率",symbol:"w",value:3,min:0.1,max:10,step:0.1}]}。
        formula 必须基于原文关系，不可编造；只允许 +-*/^ 括号和 sin cos tan asin acos atan exp log ln sqrt abs pow min max 及 pi e。
        区间内必须有限实数。采访核实输出 checks 数组 quote/claim/verdict，不虚构数值模型。
        """;
    public static JsonNode normalize(JsonNode input,ObjectMapper json) {
        if(!(input instanceof ObjectNode object))return input;
        var m=object.path("model");var defaults=DEFAULTS.get(m.path("kind").asText());
        if(defaults==null||!(m instanceof ObjectNode model))return object;
        var values=json.createArrayNode();boolean filled=false;
        for(int i=0;i<defaults.length;i++) {var v=m.path("values").path(i);if(v.isNumber()&&Double.isFinite(v.asDouble()))values.add(v);else{values.add(defaults[i]);filled=true;}}
        model.set("values",values);
        if(filled)object.put("answer","材料未给全参数，已按标准默认条件启动模拟实验，可直接在右侧滑块修改为你的实测条件。\n"+object.path("answer").asText(""));
        return object;
    }
}
