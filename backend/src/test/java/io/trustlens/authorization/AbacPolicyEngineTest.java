package io.trustlens.authorization;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AbacPolicyEngineTest {
    private final AbacPolicyEngine engine=new AbacPolicyEngine();
    private final ObjectMapper mapper=new ObjectMapper();

    private com.fasterxml.jackson.databind.JsonNode policy(String json)throws Exception{return mapper.readTree(json);}
    private AbacPolicyEngine.Context context(boolean enabled,boolean active,boolean owner,String action,
        String actorDept,String docDept,String classification,int clearance,int required,
        Set<String> roles,Set<String> tags,boolean grant){
        return new AbacPolicyEngine.Context(enabled,active,owner,action,actorDept,docDept,classification,
            clearance,required,roles,tags,java.util.Map.of(),java.util.Map.of(),grant);
    }

    @Test void disabledAccountAlwaysDeniedEvenIfOwner()throws Exception{
        var p=policy("{\"rules\":[{\"id\":\"owner\",\"effect\":\"ALLOW\",\"priority\":1,\"actions\":[\"*\"]}]}");
        var d=engine.evaluate(p,context(false,true,true,"READ","CSE","CSE","PUBLIC",5,0,Set.of("SECURITY_ADMIN"),Set.of(),true));
        assertFalse(d.allowed());assertEquals("ACCOUNT_DISABLED",d.reasonCode());
    }
    @Test void inactiveDocumentAlwaysDenied()throws Exception{
        var p=policy("{\"rules\":[{\"id\":\"all\",\"effect\":\"ALLOW\",\"priority\":1,\"actions\":[\"*\"]}]}");
        var d=engine.evaluate(p,context(true,false,true,"READ","CSE","CSE","PUBLIC",5,0,Set.of(),Set.of(),true));
        assertFalse(d.allowed());assertEquals("DOCUMENT_INACTIVE",d.reasonCode());
    }
    @Test void clearanceIsAnUnbypassableAttributeGuardrail()throws Exception{
        var p=policy("{\"rules\":[{\"id\":\"allow\",\"effect\":\"ALLOW\",\"priority\":999,\"actions\":[\"READ\"]}]}");
        var d=engine.evaluate(p,context(true,true,true,"READ","CSE","CSE","RESTRICTED",1,4,Set.of("SECURITY_ADMIN"),Set.of(),true));
        assertFalse(d.allowed());assertEquals("INSUFFICIENT_CLEARANCE",d.reasonCode());
    }
    @Test void combinesRoleDepartmentClassificationTagsAndClearance()throws Exception{
        var p=policy("{\"rules\":[{\"id\":\"faculty-research\",\"effect\":\"ALLOW\",\"priority\":10,\"actions\":[\"READ\"],\"subject\":{\"roles\":[\"FACULTY\"],\"sameDepartment\":true,\"clearanceMin\":2},\"resource\":{\"classifications\":[\"CONFIDENTIAL\"],\"tagsAny\":[\"research\"]}}]}");
        var allow=engine.evaluate(p,context(true,true,false,"READ","CSE","CSE","CONFIDENTIAL",3,2,Set.of("FACULTY"),Set.of("research"),false));
        var wrongDept=engine.evaluate(p,context(true,true,false,"READ","ECE","CSE","CONFIDENTIAL",3,2,Set.of("FACULTY"),Set.of("research"),false));
        var wrongTag=engine.evaluate(p,context(true,true,false,"READ","CSE","CSE","CONFIDENTIAL",3,2,Set.of("FACULTY"),Set.of("finance"),false));
        assertTrue(allow.allowed());assertFalse(wrongDept.allowed());assertFalse(wrongTag.allowed());
    }
    @Test void denyByDefaultAndExplicitDenyRuleWork()throws Exception{
        var empty=policy("{\"rules\":[]}");
        var denied=engine.evaluate(empty,context(true,true,false,"READ","CSE","CSE","INTERNAL",2,0,Set.of("FACULTY"),Set.of(),false));
        assertFalse(denied.allowed());assertEquals("NO_MATCHING_POLICY",denied.reasonCode());
        var deny=policy("{\"rules\":[{\"id\":\"blocked-tag\",\"effect\":\"DENY\",\"priority\":100,\"actions\":[\"DOWNLOAD\"],\"resource\":{\"tagsAny\":[\"exam\"]},\"reasonCode\":\"EXAM_RESTRICTED\"}]}");
        var blocked=engine.evaluate(deny,context(true,true,false,"DOWNLOAD","CSE","CSE","INTERNAL",4,0,Set.of("FACULTY"),Set.of("exam"),false));
        assertFalse(blocked.allowed());assertEquals("EXAM_RESTRICTED",blocked.reasonCode());
    }
}
