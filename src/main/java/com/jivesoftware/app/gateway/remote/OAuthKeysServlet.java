package com.jivesoftware.app.gateway.remote;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.log4j.*;

public class OAuthKeysServlet extends HttpServlet {

    public static String ConsumerKey = "Set your Jive app consumer key here!";
    public static String ConsumerSecret = "Set your Jive app consumer secret here!";

    private static final Logger log = Logger.getLogger(AuthFilter.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        ServletOutputStream out = resp.getOutputStream();
        log.info("{ \"consumerKey\": \"" + ConsumerKey + "\", \"consumerSecret\": \"" + ConsumerSecret + "\" }");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    
        if(ConsumerKey == null || ConsumerSecret == null) resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Consumer key and secret are required");
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
