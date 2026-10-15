package com.jobportal.security;

import com.jobportal.domain.Role;

/** The logged-in user as seen by controllers (set by JwtAuthFilter). */
public record AuthUser(Long id, String email, Role role) {}
