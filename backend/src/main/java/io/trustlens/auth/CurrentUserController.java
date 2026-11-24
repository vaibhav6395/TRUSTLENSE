package io.trustlens.auth;

/*
 * Current-user endpoint is implemented in AuthController so authentication,
 * login, logout, CSRF bootstrap, and principal inspection stay together.
 * This class is retained temporarily to avoid breaking older references.
 */
final class CurrentUserController {
    private CurrentUserController() {}
}
