package com.argus.rag.college;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PracticeModelProtocolTest {
 @Test void sevenKindsFillOnlyMissingValues()throws Exception{var j=new ObjectMapper();assertEquals(7,PracticeModelProtocol.DEFAULTS.size());for(var kind:PracticeModelProtocol.DEFAULTS.keySet()){var input=j.createObjectNode().put("answer","依据材料");input.putObject("model").put("kind",kind).putArray("values").add(2);var out=PracticeModelProtocol.normalize(input,j);assertEquals(2,out.path("model").path("values").get(0).asDouble());assertEquals(PracticeModelProtocol.DEFAULTS.get(kind).length,out.path("model").path("values").size());assertTrue(out.path("answer").asText().startsWith("材料未给全参数"));}}
 @Test void formulaPreservesProtocol()throws Exception{var j=new ObjectMapper();var n=j.readTree("{\"answer\":\"公式来自原文\",\"model\":{\"kind\":\"formula\",\"expression\":\"sin(x)\",\"xMin\":0,\"xMax\":6}}");assertEquals(n,PracticeModelProtocol.normalize(n.deepCopy(),j));assertTrue(PracticeModelProtocol.INSTRUCTIONS.contains("formula"));}
}
