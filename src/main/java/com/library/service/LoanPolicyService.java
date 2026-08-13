package com.library.service;

import com.library.data.LoanPolicyRepository;
import com.library.domain.LoanPolicy;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;

public final class LoanPolicyService {
    private final LoanPolicyRepository policies;
    private final AuthorizationService authorization;
    private final AuditService audit;

    public LoanPolicyService(
            LoanPolicyRepository policies,
            AuthorizationService authorization,
            AuditService audit) {
        this.policies = policies;
        this.authorization = authorization;
        this.audit = audit;
    }

    public LoanPolicy current() throws SQLException {
        return policies.load();
    }

    public int loanDays() throws SQLException {
        return current().loanDays();
    }

    public LoanPolicy update(User actor, LoanPolicy policy) throws SQLException {
        authorization.require(actor, Permission.MANAGE_USERS);
        policies.save(policy);
        audit.record(
                actor.id(),
                "UPDATE_POLICY",
                "{\"loanDays\":" + policy.loanDays()
                        + ",\"maxRenewals\":" + policy.maxRenewals()
                        + ",\"borrowLimit\":" + policy.borrowLimit() + "}");
        return policy;
    }
}
