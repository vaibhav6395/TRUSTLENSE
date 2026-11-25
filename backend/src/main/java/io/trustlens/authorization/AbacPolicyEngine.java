package io.trustlens.authorization;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class AbacPolicyEngine {
    public record Context(
        boolean accountEnabled, boolean documentActive, boolean owner,
        String action, String actorDepartment, String documentDepartment,
        String classification, int actorClearance, int requiredClearance,
        Set<String> roles, Set<String> documentTags, java.util.Map<String,String> subjectAttributes,
        java.util.Map<String,String> resourceAttributes, boolean activeGrant
    ) {}
    public record Decision(boolean allowed, String reasonCode, String explanation) {}

    public Decision evaluate(JsonNode policy, Context c) {
        if (!c.accountEnabled()) return deny("ACCOUNT_DISABLED", "The account is disabled.");
        if (!c.documentActive()) return deny("DOCUMENT_INACTIVE", "The document is not active.");
        if (c.actorClearance() < c.requiredClearance())
            return deny("INSUFFICIENT_CLEARANCE", "The account clearance is below the document requirement.");

        JsonNode rules = policy == null ? null : policy.path("rules");
        if (rules != null && rules.isArray()) {
            java.util.List<JsonNode> ordered = new java.util.ArrayList<>();
            rules.forEach(ordered::add);
            ordered.sort((a,b) -> Integer.compare(b.path("priority").asInt(0), a.path("priority").asInt(0)));
            for (JsonNode rule : ordered) {
                if (!matches(rule,c)) continue;
                String effect = rule.path("effect").asText("DENY");
                String reason = rule.path("reasonCode").asText("POLICY_RULE_MATCH");
                String explanation = rule.path("description").asText("A published policy rule matched this request.");
                return "ALLOW".equalsIgnoreCase(effect)
                    ? new Decision(true, reason, explanation)
                    : deny(reason, explanation);
            }
        }
        return deny("NO_MATCHING_POLICY", "No published policy rule permits this action (deny by default).");
    }

    private boolean matches(JsonNode r, Context c) {
        if (!contains(r.path("actions"), c.action()) && !contains(r.path("actions"), "*")) return false;
        JsonNode s = r.path("subject");
        JsonNode resource = r.path("resource");
        JsonNode conditions = r.path("conditions");
        if (s.has("roles") && s.path("roles").isArray() && s.path("roles").size()>0 &&
            !intersects(s.path("roles"), c.roles())) return false;
        if (s.has("departments") && s.path("departments").isArray() && s.path("departments").size()>0 &&
            !contains(s.path("departments"), c.actorDepartment())) return false;
        if (s.path("sameDepartment").asBoolean(false) &&
            (c.actorDepartment()==null || !c.actorDepartment().equals(c.documentDepartment()))) return false;
        if (s.has("clearanceMin") && c.actorClearance() < s.path("clearanceMin").asInt(0)) return false;
        if(!matchesAttributes(s.path("attributes"),c.subjectAttributes())) return false;
        if (resource.has("classifications") && resource.path("classifications").isArray() &&
            resource.path("classifications").size()>0 && !contains(resource.path("classifications"),c.classification())) return false;
        if (resource.has("departments") && resource.path("departments").isArray() &&
            resource.path("departments").size()>0 && !contains(resource.path("departments"),c.documentDepartment())) return false;
        if (resource.path("sameDepartment").asBoolean(false) &&
            (c.actorDepartment()==null || !c.actorDepartment().equals(c.documentDepartment()))) return false;
        if (resource.has("tagsAny") && resource.path("tagsAny").isArray() &&
            resource.path("tagsAny").size()>0 && !intersects(resource.path("tagsAny"),c.documentTags())) return false;
        if(!matchesAttributes(resource.path("attributes"),c.resourceAttributes())) return false;
        if (conditions.path("owner").isBoolean() && conditions.path("owner").asBoolean()!=c.owner()) return false;
        if (conditions.path("grantRequired").asBoolean(false) && !c.activeGrant()) return false;
        String grantPermission = conditions.path("grantPermission").asText("");
        if (!grantPermission.isBlank() && (!c.activeGrant() || !grantPermission.equals(c.action()))) return false;
        return true;
    }

    private boolean matchesAttributes(JsonNode required,java.util.Map<String,String> actual) {
        if(required==null||required.isMissingNode()||required.isNull()) return true;
        if(!required.isObject()) return false;
        var fields=required.fields();
        while(fields.hasNext()){
            var field=fields.next();
            if(!actual.containsKey(field.getKey())||!actual.get(field.getKey()).equals(field.getValue().asText())) return false;
        }
        return true;
    }
    private boolean contains(JsonNode array, String value) {
        if (value==null || !array.isArray()) return false;
        for(JsonNode n:array) if(n.asText().equalsIgnoreCase(value)) return true;
        return false;
    }
    private boolean intersects(JsonNode array, Set<String> values) {
        if(!array.isArray()) return false;
        for(JsonNode n:array) if(values.stream().anyMatch(v->v.equalsIgnoreCase(n.asText()))) return true;
        return false;
    }
    private Decision deny(String code,String why) { return new Decision(false,code,why); }
}
