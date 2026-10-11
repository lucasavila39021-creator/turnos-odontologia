package com.clinica.turnos.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SSR login view — C-03 D-04.
 * C-11: GET / se mudó a ShellController (redirect por rol); acá solo /login.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
