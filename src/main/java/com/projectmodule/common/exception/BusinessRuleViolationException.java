package com.projectmodule.common.exception;

/**
 * A request was well-formed but would break a domain invariant.
 *
 * <p>Distinct from {@link ValidationException}: the input was understood and is syntactically
 * acceptable, but applying it would leave the domain in an invalid state.
 */
public final class BusinessRuleViolationException extends ProjectModuleException {

    public BusinessRuleViolationException(String message) {
        super(ErrorType.BUSINESS_RULE_VIOLATION, message);
    }
}
