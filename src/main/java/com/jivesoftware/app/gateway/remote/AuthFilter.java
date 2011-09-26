package com.jivesoftware.app.gateway.remote;

import net.oauth.*;
import net.oauth.server.OAuthServlet;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.log4j.*;
import java.io.PrintStream;

public class AuthFilter implements Filter
{
    
    private static final Logger log = Logger.getLogger(AuthFilter.class);
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            log.info("Request recieved");

            // create request message 
            OAuthMessage requestMessage = OAuthServlet.getMessage((HttpServletRequest)request, null);

            // log it
	    log.info("Request message: " + requestMessage);
            OAuthServiceProvider provider = new OAuthServiceProvider(null, null, null);
            OAuthConsumer consumer = new OAuthConsumer(null, OAuthKeysServlet.ConsumerKey, OAuthKeysServlet.ConsumerSecret, provider);
            OAuthAccessor accessor = new OAuthAccessor(consumer);

            SimpleOAuthValidator validator = new SimpleOAuthValidator();
            // should we remember nonce and timestamp to verify it is not a replay?


            validator.validateMessage(requestMessage, accessor);
            chain.doFilter(request, response);
        }
        catch(Exception e) {
            log.error(e);
            ((HttpServletResponse)response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            e.printStackTrace(new PrintStream(response.getOutputStream()));
        }
    }

    @Override
    public void destroy() {
    }
}
