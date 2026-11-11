package com.projectmodule.account.api.dto;

import com.projectmodule.account.application.SignedInAccount;
import java.time.Instant;

public record SessionResponse(String token, Instant expiresAt, AccountResponse account) {

    public static SessionResponse from(SignedInAccount signedIn) {
        return new SessionResponse(signedIn.token(), signedIn.expiresAt(), AccountResponse.from(signedIn.account()));
    }
}
