package au.com.equifax.cicddemo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * PRUEBA FINAL — Código con deuda técnica y vulnerabilidades de seguridad.
 *
 * Propósito: demostrar que SonarQube detecta problemas y el Quality Gate
 * bloquea el despliegue automáticamente.
 *
 * Escenario A (falla esperada):
 *   Hacer commit con este archivo tal cual → SonarQube detecta Security Hotspots
 *   → Quality Gate falla → pipeline abortado antes del deploy.
 *
 * Escenario B (éxito esperado):
 *   Eliminar este archivo o corregir los problemas → Quality Gate pasa
 *   → pipeline construye y despliega la nueva versión.
 */
@RestController
public class DebtController {

    // Security Hotspot S2068: contraseña hardcodeada en el código fuente
    private static final String DB_PASSWORD = "admin123";

    // Security Hotspot S4790: MD5 es un algoritmo criptográfico débil
    public String hashValue(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(input.getBytes());
        return new String(hash);
    }

    // Security Hotspot S2245: Random no es criptográficamente seguro
    public String generateToken() {
        Random random = new Random();
        return String.valueOf(random.nextLong());
    }

    // Vulnerability S2077: inyección SQL por concatenación de cadenas
    // Code Smell: uso de System.out.println en vez de un logger
    @GetMapping("/api/search")
    public String search(@RequestParam String query) {
        String sql = "SELECT * FROM users WHERE name = '" + query + "'";
        System.out.println("Ejecutando consulta: " + sql);
        return sql;
    }
}
