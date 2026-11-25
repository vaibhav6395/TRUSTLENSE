package io.trustlens.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class SessionAccountRefreshFilterTest {
    private UserDetailsService users;
    private SessionAccountRefreshFilter filter;

    @BeforeEach
    void setUp() {
        users = mock(UserDetailsService.class);
        filter = new SessionAccountRefreshFilter(users);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String username) {
        var principal = User.withUsername(username).password("unused").roles("OLD_ROLE").build();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @Test
    void refreshesAuthoritiesFromCurrentAccountOnEveryRequest() throws Exception {
        authenticate("faculty");
        when(users.loadUserByUsername("faculty"))
            .thenReturn(User.withUsername("faculty").password("unused").roles("FACULTY").build());

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals("ROLE_FACULTY", SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities().iterator().next().getAuthority());
        verify(users).loadUserByUsername("faculty");
    }

    @Test
    void disabledAccountIsRejectedAndExistingSessionInvalidated() throws Exception {
        authenticate("faculty");
        when(users.loadUserByUsername("faculty"))
            .thenReturn(User.withUsername("faculty").password("unused").disabled(true).roles("FACULTY").build());

        var request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(request.getSession(false));
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(session.isInvalid());
    }

    @Test
    void deletedAccountIsRejected() throws Exception {
        authenticate("removed-user");
        when(users.loadUserByUsername("removed-user"))
            .thenThrow(new UsernameNotFoundException("missing"));

        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void anonymousRequestsPassWithoutDatabaseLookup() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "key", "anonymous", java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        verifyNoInteractions(users);
    }
}
