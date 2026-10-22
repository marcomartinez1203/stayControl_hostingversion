import { iniciarSesion } from "../services/authService.js";

const passwordInput = document.getElementById("password");
const togglePassword = document.getElementById("togglePassword");
const loginButton = document.getElementById("loginButton");
const emailInput = document.getElementById("email");
const notice = document.getElementById("loginNotice");

for (const input of [emailInput, passwordInput]) {
    input.addEventListener("keydown", event => {
        if (event.key === "Enter") { event.preventDefault(); loginButton.click(); }
    });
}

togglePassword.addEventListener("click", () => {
    const isPassword = passwordInput.type === "password";

    passwordInput.type = isPassword ? "text" : "password";

    togglePassword.setAttribute(
        "aria-label",
        isPassword
            ? "Ocultar contraseña"
            : "Mostrar contraseña"
    );
});

loginButton.addEventListener("click", async event => {
    event.preventDefault();
    if (loginButton.disabled) { return; }
    const email = emailInput.value.trim();
    const password = passwordInput.value;

    if (!email || !password) {
        notice.textContent = "Completa todos los campos.";
        return;
    }
    if (!emailInput.reportValidity()) { return; }

    loginButton.disabled = true;
    notice.textContent = "Iniciando sesión…";
    try {
        await iniciarSesion(email, password);
        passwordInput.value = "";
        window.location.assign("/dashboard");
    } catch (error) {
        notice.textContent = error.status === 401
            ? "Correo o contraseña incorrectos. Debes registrar la cuenta o aprovisionar el administrador antes de entrar."
            : error.status ? error.message : "No se pudo conectar con el servidor. Comprueba que Spring Boot esté funcionando.";
    } finally {
        loginButton.disabled = false;
    }
});
