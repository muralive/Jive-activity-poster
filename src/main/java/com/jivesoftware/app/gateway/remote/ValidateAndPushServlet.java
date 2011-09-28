package com.jivesoftware.app.gateway.remote;

import com.jivesoftware.activitystreams.v1.rest.ActivityStreamRepresentation;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthException;
import oauth.signpost.signature.AuthorizationHeaderSigningStrategy;
import oauth.signpost.signature.HmacSha1MessageSigner;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;


/**
 * This is an example servlet to push an activity stream to the jive gateway server. It is written for example
 * purposes only and should not be used in production by app developers without significant rework.
 */
public class ValidateAndPushServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(ValidateAndPushServlet.class);

    // GAE doesn't support javax.net.ssl.HttpsURLConnection or does certificate validation
    // private static final String GATEWAY_BASE_URL = "https://gateway.jivesoftware.com/gateway/api/activity/v1/";

    // GAE says Google App Engine does not support reverse-lookup of an IP address by host name: gateway.jivesoftware.com
    // private static final String GATEWAY_BASE_URL = "http://gateway.jivesoftware.com/gateway/api/activity/v1/";

    // Use IP address of gateway to post to it
    private static final String GATEWAY_BASE_URL = "http://209.46.55.9/gateway/api/activity/v1/";

    private static final String ERROR_HEADER = "X-Error-Message";

    private static HashMap<Long, JiveAppPair> ids = new HashMap<Long, JiveAppPair>();

    public static void add(Long userId, String jiveId, String appId) {
        ValidateAndPushServlet.ids.put(userId, new JiveAppPair(jiveId, appId));
        logger.info(String.format("Install app registered: user id: %d, Jive Id: %s, App Id: %s", userId, jiveId, appId));
    }

    public static void remove(Long userId, String jiveId, String appId) {
        ValidateAndPushServlet.ids.remove(userId);
        logger.info(String.format("Uninstall app registered: user id: %d, Jive Id: %s, App Id: %s", userId, jiveId, appId));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // verify that the appID, userID and instanceID are set
        long uid = 0;
        String jiveId = "", appId = "";
        String consumerKey = req.getParameter("consumerKey");
        String consumerSecret = req.getParameter("consumerSecret");

        try {
            if(req.getParameter("uid") != null) uid = Long.valueOf(req.getParameter("uid"));
        }
        catch(NumberFormatException nfe) {
            resp.setHeader("X-Error-Message", "URL parameter uid must be set to a valid user id");
            resp.sendError(400);
        }
        if(uid > 0) {
            JiveAppPair jiveAppPair = ids.get(uid);
            if(jiveAppPair != null) {
                jiveId = jiveAppPair.getJiveId();
                appId = jiveAppPair.getAppId();
            }
            else
            {
                // build the appid from consumer key & use app-sandbox's jive id
                jiveId = "99b3427b-202e-4891-a50e-1fb5e7b173be";
                if(consumerKey != null && consumerKey.length() == 32) {
                    appId = consumerKey.substring(0, 8) + '-' + consumerKey.substring(8, 12) + '-' + consumerKey.substring(12, 16) + '-' + consumerKey.substring(16, 20) + '-' + consumerKey.substring(20);
                }
            }
        }
        else if(ids.keySet().size() > 0) {
            Map.Entry<Long, JiveAppPair> firstOne = ids.entrySet().iterator().next();
            uid = firstOne.getKey();
            jiveId = firstOne.getValue().getJiveId();
            appId = firstOne.getValue().getAppId();
        }

        if (uid == 0) {
            resp.setHeader(ERROR_HEADER, "No app is registered with the demo server. Please follow the steps outlined" +
                    " in the tutorial to have an app register with this demo server. If you have restarted this server" +
                    " note that you will have to create and install a new app into the sandbox instance for app" +
                    " registration to occur");
            resp.setStatus(400);
            return;
        }

        logger.info(String.format("Posting to: user id: %d, Jive Id: %s, App Id: %s", uid, jiveId, appId));
        // retrieve post form fields. We're just going to assume that the fields have been
        // null tested already by the javascript in the form
        int targetType = NumberUtils.toInt(req.getParameter("targetType"), 0);
        String json = req.getParameter("json");

        // validate the json is in the proper format
        String errorMsg = validateJson(json);

        if (!StringUtils.isBlank(errorMsg)) {
            resp.setHeader(ERROR_HEADER, errorMsg);
            resp.setStatus(400);
            return;
        }

        // execute the push to the jive gateway server
        Resp r = doPush(req, consumerKey, consumerSecret, targetType, json, uid, jiveId, appId);

        // handle the response
        if (!StringUtils.isBlank(r.getErrorMessage())) {
            resp.setHeader(ERROR_HEADER, r.getErrorMessage());
        }
        resp.setStatus(r.getResponseCode());
    }

    /**'
     * We use jackson here to parse and map the json into the same
     * data structure we use on the gateway server. Any failures
     * are logged and returned to the user
     *
     * @param json the json to validate
     * @return an error message or null if no error occurred
     */
    private String validateJson(String json) {
        if (StringUtils.isBlank(json)) {
            return "You must supply an activity stream to continue";
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.readValue(json, ActivityStreamRepresentation.class);
        }
        catch (JsonMappingException e) {
            logger.error(e.getMessage(), e);
            return "The supplied json failed to conform to the activity stream format: " + e.getMessage();
        }
        catch (JsonParseException e) {
            logger.error(e.getMessage(), e);
            return "The supplied json had a formatting error in it: " + e.getMessage();
        }
        catch (IOException e) {
            logger.error(e.getMessage(), e);
            return e.getMessage();
        }

        return null;
    }

    /**
     * Execute the push to the jive gateway server
     *
     * @param consumerKey the app's consumer key
     * @param consumerSecret the app's comsumer secret
     * @param targetType the target of the activity stream (0 = normal, 1 = inbox)
     * @param json the json to push
     * @return a {@link Resp} object
     */
    private Resp doPush(HttpServletRequest req, String consumerKey, String consumerSecret, int targetType, String json, long uid, String jiveId, String appId) {
        try {
            // create the request
            HttpPost post = new HttpPost(getPostUrl(targetType, uid, jiveId, appId));
            StringEntity entity = new StringEntity(json, "UTF-8");
            entity.setContentType("application/json");
            post.setEntity(entity);

            // The jive gateway *requires* the use of this header to denote the app UUID
            // the post is associated with
            post.setHeader("X-Jive-App-Id", appId);

            // setup oauth
            OAuthConsumer consumer = new CommonsHttpOAuthConsumer(consumerKey, consumerSecret);
            consumer.setSigningStrategy(new AuthorizationHeaderSigningStrategy());
            consumer.setSendEmptyTokens(false);
            consumer.setMessageSigner(new HmacSha1MessageSigner());
            // sign the post
            consumer.sign(post);

            // execute the post
            int responseCode = 200;
            String errorHeaderValue = null;
            if(req.getServerName().endsWith("appspot.com"))
            {
                // GAE has serious restrictions on which particular classes are allowed
                // eg 1: java.lang.NoClassDefFoundError: java.net.Socket is a restricted class.
                // eg 2:java.lang.NoClassDefFoundError: javax.net.ssl.HttpsURLConnection is a restricted class.
                // So we have to use a direct Java URLConnection class as http client.
                String message = URLEncoder.encode("my message", "UTF-8");

                URL url = new URL(post.getURI().toString());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.addRequestProperty("Content-Type", "application/json");
                connection.addRequestProperty("X-Jive-App-Id", appId);
                connection.addRequestProperty("Authorization", post.getFirstHeader("Authorization").getValue());
                connection.addRequestProperty("Content-Length", Integer.toString(json.length()));

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(json);
                writer.close();

                responseCode = connection.getResponseCode();
                if(responseCode != HttpURLConnection.HTTP_OK) {
                    errorHeaderValue = connection.getHeaderField("X-Jive-Error-Message");
                }
            }
            else {
                DefaultHttpClient httpClient = new DefaultHttpClient();
                HttpResponse response = httpClient.execute(post);

                // parse the response
                responseCode = response.getStatusLine().getStatusCode();
                // any error messages will be returned in this header
                Header header = response.getFirstHeader("X-Jive-Error-Message");
                if(header != null) errorHeaderValue = header.getValue();
            }

            return new Resp(responseCode, errorHeaderValue);
        }
        catch (OAuthException e) {
            logger.error(e.getMessage(), e);
            e.printStackTrace();
            return new Resp(401, "An error occurred signing the request to the gateway server: " + e.getMessage());
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
            e.printStackTrace();
            return new Resp(500, "An unhandled exception occurred while executing the request to the gateway " +
                    "server: " + e.getMessage());
        }
    }

    private String getPostUrl(int type, long uid, String jiveId, String appId) {
        String url = GATEWAY_BASE_URL;
        if (type == 0) {
            url += "update";
        }
        else {
            url += "updateInbox";
        }

        String gatewayUrl = url + "/" + jiveId + "/" + appId + "/" + uid;
        logger.info("Posting to :" + gatewayUrl);
        return gatewayUrl;
    }

    private class Resp {
        private int responseCode;
        private String errorMessage;

        private Resp(int responseCode, String errorMessage) {
            this.responseCode = responseCode;
            this.errorMessage = errorMessage;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    static class JiveAppPair {
        private String jiveId, appId;

        public JiveAppPair(String jiveId, String appId) {
            this.jiveId = jiveId;
            this.appId = appId;
        }

        public String getJiveId() {
            return jiveId;
        }

        public String getAppId() {
            return appId;
        }
    }
}
