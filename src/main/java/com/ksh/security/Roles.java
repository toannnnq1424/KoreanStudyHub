package com.ksh.security;

/**
 * SpEL-compatible role constants for {@code @PreAuthorize} expressions
 * and {@code hasRole(...)} / {@code hasAnyRole(...)} filter chain calls.
 *
 * <p>Spring Security expression engine works on string literals, so we keep
 * these as compile-time constants of {@link Role#name()}. This eliminates
 * the "LECTURER" / "LEADER" / "ADMIN" string duplication across
 * {@code SecurityConfig}, controllers, and Thymeleaf {@code sec:authorize}.
 *
 * <p>Common group:
 * {@link #LECTURER_OR_ABOVE} = LECTURER, LEADER, ADMIN — i.e. anyone able to
 * teach or manage classes.
 */
public final class Roles {

    public static final String STUDENT  = "STUDENT";
    public static final String LECTURER = "LECTURER";
    public static final String LEADER     = "LEADER";
    public static final String ADMIN    = "ADMIN";

    /** Comma-separated list usable in hasAnyRole('LECTURER','LEADER','ADMIN'). */
    public static final String LECTURER_OR_ABOVE = "'LECTURER','LEADER','ADMIN'";

    /** Full SpEL for @PreAuthorize on lecturer-side endpoints. */
    public static final String PREAUTH_LECTURER_OR_ABOVE =
            "hasAnyRole(" + LECTURER_OR_ABOVE + ")";

    /** Exact lecturer role for practice authoring owned by lecturers only. */
    public static final String PREAUTH_LECTURER = "hasRole('LECTURER')";

    /** Exact student role for learner-only practice progress and attempt pages. */
    public static final String PREAUTH_STUDENT = "hasRole('STUDENT')";

    /** Comma-separated list usable for governance and reviewer-only routes. */
    public static final String LEADER_OR_ADMIN = "'LEADER','ADMIN'";

    /** Full SpEL for governance endpoints that lecturers must never enter. */
    public static final String PREAUTH_LEADER_OR_ADMIN =
            "hasAnyRole(" + LEADER_OR_ADMIN + ")";

    private Roles() {
        // utility class
    }
}
