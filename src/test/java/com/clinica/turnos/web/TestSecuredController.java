package com.clinica.turnos.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test controller for RBAC 403 checks — only used in tests.
 */
@RestController
@RequestMapping("/api/test")
public class TestSecuredController {

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "admin";
    }

    @GetMapping("/secretaria-only")
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN')")
    public String secretariaOnly() {
        return "secretaria";
    }

    @GetMapping("/public")
    public String pub() {
        return "public";
    }
}
