package io.trustlens.authorization;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {
    private final PolicyLifecycleService policies;
    private final AbacPolicyEngine engine;
    public PolicyController(PolicyLifecycleService policies,AbacPolicyEngine engine){this.policies=policies;this.engine=engine;}

    @GetMapping
    public Map<String,Object> list(){return Map.of("versions",policies.list(),"activeRules",policies.activeRules(),"activeVersionId",
        policies.activeVersionId()==null?"":policies.activeVersionId().toString());}

    @GetMapping("/{id}")
    public Map<String,Object> get(@PathVariable UUID id){return policies.get(id);}

    @PostMapping("/drafts")
    public Map<String,Object> create(@RequestBody PolicyLifecycleService.DraftRequest request,Authentication auth){
        return policies.createDraft(request,auth.getName());
    }

    @PostMapping("/{id}/submit")
    public Map<String,Object> submit(@PathVariable UUID id,Authentication auth){return policies.submit(id,auth.getName());}

    @PostMapping("/{id}/approve")
    public Map<String,Object> approve(@PathVariable UUID id,@RequestBody(required=false) PolicyLifecycleService.ReviewRequest body,Authentication auth){
        return policies.approve(id,body,auth.getName());
    }

    @PostMapping("/{id}/reject")
    public Map<String,Object> reject(@PathVariable UUID id,@RequestBody(required=false) PolicyLifecycleService.ReviewRequest body,Authentication auth){
        return policies.reject(id,body,auth.getName());
    }

    @PostMapping("/{id}/impact-verify")
    public Map<String,Object> impact(@PathVariable UUID id){return policies.impact(id);}

    @PostMapping("/simulate")
    public Map<String,Object> simulate(@RequestBody SimulationRequest request){
        return policies.simulate(request.rules(),request.scenarios(),engine);
    }

    public record SimulationRequest(com.fasterxml.jackson.databind.JsonNode rules,com.fasterxml.jackson.databind.JsonNode scenarios){}
}
