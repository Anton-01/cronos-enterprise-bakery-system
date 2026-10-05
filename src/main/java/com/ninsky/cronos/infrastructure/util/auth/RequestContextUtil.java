package com.ninsky.cronos.infrastructure.util.auth;

import jakarta.servlet.http.HttpServletRequest;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestContextUtil {

    // Inicializamos el analizador una sola vez (es una operación pesada).
    // Ocultamos las estadísticas de carga y agregamos caché para máximo rendimiento.
    private static final UserAgentAnalyzer uaa = UserAgentAnalyzer.newBuilder()
            .hideMatcherLoadStats()
            .withCache(10000)
            .build();

    public String getClientIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "Unknown";
        }

        String[] headersToCheck = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
        };

        for (String header : headersToCheck) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Si hay múltiples IPs (Proxies encadenados), tomar la primera que es la original
                return ip.contains(",") ? ip.split(",")[0].trim() : ip;
            }
        }

        return request.getRemoteAddr();
    }

    public String getUserAgent() {
        HttpServletRequest request = getCurrentRequest();
        return (request != null && request.getHeader("User-Agent") != null)
                ? request.getHeader("User-Agent")
                : "Unknown";
    }

    public String getBrowser() {
        String uaString = getUserAgent();
        if ("Unknown".equals(uaString)) return "Unknown";
        UserAgent agent = uaa.parse(uaString);
        return agent.getValue("AgentNameVersion"); // Ej: "Chrome 120"
    }

    public String getOperatingSystem() {
        String uaString = getUserAgent();
        if ("Unknown".equals(uaString)) return "Unknown";
        UserAgent agent = uaa.parse(uaString);
        return agent.getValue("OperatingSystemNameVersion"); // Ej: "Windows 11"
    }

    public String getDevice() {
        String uaString = getUserAgent();
        if ("Unknown".equals(uaString)) return "Unknown";
        UserAgent agent = uaa.parse(uaString);
        return agent.getValue("DeviceClass"); // Ej: "Desktop", "Mobile", "Tablet"
    }

    /** "Browser major / OS" (e.g. "Chrome 140 / macOS") of a stored User-Agent; null when blank. */
    public String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank() || "Unknown".equals(userAgent)) {
            return null;
        }
        UserAgent agent = uaa.parse(userAgent);
        String browser = agent.getValue("AgentNameVersionMajor");
        String os = agent.getValue("OperatingSystemName");
        boolean known = !browser.startsWith("Unknown") && !browser.startsWith("??");
        return known ? browser + " / " + os : userAgent;
    }

    public String getLocation() {
        // Listo para integrar MaxMind GeoIP2 o similar usando el IP devuelto por getClientIp()
        return "Unknown";
    }

    /** Header from the current request, or null if there is none (e.g. no request bound to this thread) or the header is absent. */
    public String getHeader(String name) {
        HttpServletRequest request = getCurrentRequest();
        return request != null ? request.getHeader(name) : null;
    }

    /** Absolute URL the client targeted (scheme/host/port/path, no query/fragment) — already reflects X-Forwarded-* via server.forward-headers-strategy=native. */
    public String getRequestUrl() {
        HttpServletRequest request = getCurrentRequest();
        return request != null ? request.getRequestURL().toString() : null;
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
