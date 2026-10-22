package com.example.staycontrol.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping({"/", "/login", "/login.html", "/pages/login.html"})
    public String login() {
        return "redirect:/views/login.html";
    }

    @GetMapping({"/registro", "/registro.html", "/pages/registro.html"})
    public String registro() {
        return "redirect:/views/registro.html";
    }

    @GetMapping({"/recuperar-contrasena", "/recuperar-contrasena.html", "/pages/recuperar-contrasena.html"})
    public String recuperarContrasena() {
        return "redirect:/views/recuperar-contrasena.html";
    }

    @GetMapping({"/dashboard", "/dashboard.html", "/pages/dashboard.html"})
    public String dashboard() {
        return "redirect:/views/dashboard.html";
    }

    @GetMapping({"/habitaciones", "/habitaciones.html", "/pages/habitaciones.html"})
    public String habitaciones() {
        return "redirect:/views/habitaciones.html";
    }

    @GetMapping({"/reservas", "/reservas.html", "/pages/reservas.html"})
    public String reservas() {
        return "redirect:/views/reservas.html";
    }

    @GetMapping({"/finanzas", "/finanzas.html", "/pages/finanzas.html"})
    public String finanzas() {
        return "redirect:/views/finanzas.html";
    }

    @GetMapping({"/limpieza", "/limpieza.html", "/pages/limpieza.html"})
    public String limpieza() {
        return "redirect:/views/limpieza.html";
    }
}
