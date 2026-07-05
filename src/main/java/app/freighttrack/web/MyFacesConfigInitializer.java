package app.freighttrack.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Logger;

@WebListener
public class MyFacesConfigInitializer implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(MyFacesConfigInitializer.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOG.info("MyFacesConfigInitializer.contextInitialized — setting client state saving");
        ServletContext ctx = sce.getServletContext();

        ctx.setInitParameter("jakarta.faces.STATE_SAVING_METHOD", "client");
        ctx.setInitParameter("javax.faces.STATE_SAVING_METHOD", "client");

        String secret = System.getenv("FT_JSF_SECRET");
        String macSecret = System.getenv("FT_JSF_MAC_SECRET");

        if (secret != null && !secret.isEmpty()) {
            ctx.setInitParameter("org.apache.myfaces.SECRET", secret);
        }
        if (macSecret != null && !macSecret.isEmpty()) {
            ctx.setInitParameter("org.apache.myfaces.MAC_SECRET", macSecret);
        }
    }
}
