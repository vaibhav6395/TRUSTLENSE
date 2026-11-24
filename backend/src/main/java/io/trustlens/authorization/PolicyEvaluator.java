package io.trustlens.authorization;

/**
 * Small deterministic policy core. Data loading and persistence remain in
 * DocumentService; this class evaluates only facts supplied by trusted backend code.
 */
public final class PolicyEvaluator {
    private PolicyEvaluator() {}

    public record Facts(
        boolean accountEnabled,
        boolean documentActive,
        boolean owner,
        boolean publicRead,
        boolean activeGrant,
        boolean securityAdminSameDepartmentRead
    ) {}

    public record Result(boolean allowed, String reasonCode) {}

    public static Result evaluate(Facts facts) {
        if (!facts.accountEnabled()) return new Result(false, "ACCOUNT_DISABLED");
        if (!facts.documentActive()) return new Result(false, "DOCUMENT_INACTIVE");
        if (facts.owner()) return new Result(true, "DOCUMENT_OWNER");
        if (facts.publicRead()) return new Result(true, "PUBLIC_READ");
        if (facts.activeGrant()) return new Result(true, "ACTIVE_GRANT");
        if (facts.securityAdminSameDepartmentRead()) return new Result(true, "SECURITY_ADMIN_SAME_DEPARTMENT");
        return new Result(false, "NO_MATCHING_POLICY");
    }
}
