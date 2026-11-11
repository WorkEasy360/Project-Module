package com.projectmodule.account.application;

import com.projectmodule.account.domain.UserAccount;
import java.time.Instant;

/** The outcome of signing in: the account, and the token only the client will ever hold. */
public record SignedInAccount(UserAccount account, String token, Instant expiresAt) {
}
