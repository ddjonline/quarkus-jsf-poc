package app.freighttrack.web;

import app.freighttrack.session.SessionContext;
import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@WebFilter("/*")
public class SessionFilter implements Filter {

    private static final String COOKIE_NAME = "FT_SESSION";
    private static final String INSTANCE_ID = System.getenv().getOrDefault("FT_INSTANCE_ID", "dev");

    @Inject
    SessionContext sessionContext;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        res.setHeader("X-Ft-Instance", INSTANCE_ID);

        String sessionId = null;
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) {
                    sessionId = c.getValue();
                    break;
                }
            }
        }

        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
            Cookie cookie = new Cookie(COOKIE_NAME, sessionId);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(-1);
            res.addCookie(cookie);
            sessionContext.setNew(true);
        } else {
            sessionContext.setNew(false);
        }

        sessionContext.setId(sessionId);
        chain.doFilter(request, response);
    }
}
