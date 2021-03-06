<!DOCTYPE html PUBLIC"-// W3C//DTD HTML 4.01//EN">
<html>
  <head>
    <title>Activity Streams demo home server</title>
    <link href="styles.css" rel="stylesheet" type="text/css">
</head>
<body>
    <div id="wrap">
        <div id="header">
            <h1>Activity Streams Home Server Demo</h1>
        </div>
        <div id="content">
            <div class="section">
                <p>If you are seeing this page you have successfully installed the activity streams demo home server. The
                    <a href="http://developers.jivesoftware.com/community/docs/DOC-1119">tutorial</a> contains all the steps necessary to successfully
                    push activities to the sandbox server.</p>
                <p>
                    Please note that this demo stores does not store or otherwise persist any provided information at all. This means
                    that if you restart this server you will have to start fresh with a new app and go through the steps outlined in
                    the tutorial.
                </p>

                <p>
                    Before your can start sending posts, this server has to know about Jive instance and the user to post to.
                    There are two ways to accomplish this:
                    <ol>
                        <li>Your app had set the 'href' of link module pref to this server to listen to install lifecycle event.</li>
                        <li>Your app has sent a 'signed' request to this server.</li>
                    </ol><br>
                    If you are ready to post click <a href='post.jsp?uid=<%=request.getParameter("uid")%>'>here</a>.
                </p>
            </div>
        </div>
        <div id="footer">
            <small>Jive Software.</small>
        </div>
    </div>
</body>
</html>
