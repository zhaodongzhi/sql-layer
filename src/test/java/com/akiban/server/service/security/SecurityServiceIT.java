/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.server.service.security;

import com.akiban.rest.RestService;
import com.akiban.rest.RestServiceImpl;
import com.akiban.server.service.servicemanager.GuicedServiceManager;
import com.akiban.server.test.it.ITBase;
import com.akiban.sql.embedded.EmbeddedJDBCService;
import com.akiban.sql.embedded.EmbeddedJDBCServiceImpl;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SecurityServiceIT extends ITBase
{
    @Override
    protected GuicedServiceManager.BindingsConfigurationProvider serviceBindingsProvider() {
        return super.serviceBindingsProvider()
            .bindAndRequire(SecurityService.class, SecurityServiceImpl.class)
            .bindAndRequire(EmbeddedJDBCService.class, EmbeddedJDBCServiceImpl.class)
            .bindAndRequire(RestService.class, RestServiceImpl.class);
    }

    @Override
    protected Map<String, String> startupConfigProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("akserver.http.login", "basic"); // "digest"
        properties.put("akserver.postgres.login", "md5");
        properties.put("akserver.restrict_user_schema", "true");
        return properties;
    }

    protected SecurityService securityService() {
        return serviceManager().getServiceByClass(SecurityService.class);
    }

    @Before
    public void setUp() {
        int t1 = createTable("user1", "utable", "id int primary key not null");
        int t2 = createTable("user2", "utable", "id int primary key not null");        
        writeRow(t1, 1L);
        writeRow(t2, 2L);
        
        SecurityService securityService = securityService();
        securityService.addRole("rest-user");
        securityService.addRole("admin");
        securityService.addUser("user1", "password", Arrays.asList("rest-user"));
        securityService.addUser("akiban", "topsecret", Arrays.asList("rest-user", "admin"));
    }

    @After
    public void cleanUp() {
        securityService().clearAll(session());
    }

    @Test
    public void getUser() {
        SecurityService securityService = securityService();
        User user = securityService.getUser("user1");
        assertNotNull("user found", user);
        assertTrue("user has role", user.hasRole("rest-user"));
        assertFalse("user does not have role", user.hasRole("admin"));
    }

    @Test
    public void authenticate() {
        assertEquals("user1", securityService().authenticate(session(), "user1", "password").getName());
    }

    private int openRestURL(String request, String query, String userInfo)
            throws Exception {
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(getRestURL(request, query, userInfo));
        HttpResponse response = client.execute(get);
        int code = response.getStatusLine().getStatusCode();
        EntityUtils.consume(response.getEntity());
        client.getConnectionManager().shutdown();
        return code;
    }

    private URI getRestURL(String request, String query, String userInfo)
            throws Exception {
        int port = serviceManager().getServiceByClass(com.akiban.http.HttpConductor.class).getPort();
        String context = serviceManager().getServiceByClass(com.akiban.rest.RestService.class).getContextPath();
        return new URI("http", userInfo, "localhost", port, context + request, query, null);
    }

    @Test
    public void restUnauthenticated() throws Exception {
        assertEquals(HttpStatus.SC_UNAUTHORIZED,
                     openRestURL("/user1.utable/1", null, null));
    }

    @Test
    public void restAuthenticated() throws Exception {
        assertEquals(HttpStatus.SC_OK,
                     openRestURL("/entity/user1.utable/1", null, "user1:password"));
    }

    @Test
    public void restAuthenticateBadUser() throws Exception {
        assertEquals(HttpStatus.SC_UNAUTHORIZED,
                     openRestURL("/user1.utable/1", null, "user2:none"));
    }

    @Test
    public void restAuthenticateBadPassword() throws Exception {
        assertEquals(HttpStatus.SC_UNAUTHORIZED,
                     openRestURL("/user1.utable/1", null, "user1:wrong"));
    }

    @Test
    public void restAuthenticateWrongSchema() throws Exception {
        assertEquals(HttpStatus.SC_FORBIDDEN,
                     openRestURL("/entity/user2.utable/1", null, "user1:password"));
    }

    @Test
    public void restQueryAuthenticated() throws Exception {
        assertEquals(HttpStatus.SC_OK,
                     openRestURL("/sql/query", "q=SELECT+*+FROM+utable", "user1:password"));
    }

    @Test
    public void restQueryWrongSchema() throws Exception {
        assertEquals(HttpStatus.SC_NOT_FOUND,
                     openRestURL("/sql/query", "q=SELECT+*+FROM+user2.utable", "user1:password"));
    }

    static final String ADD_USER = "{\"user\":\"user3\", \"password\":\"pass\", \"roles\": [\"rest-user\"]}";

    @Test
    public void restAddDropUser() throws Exception {
        SecurityService securityService = securityService();
        assertNull(securityService.getUser("user3"));
        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(getRestURL("/security/users", null, "akiban:topsecret"));
        post.setEntity(new StringEntity(ADD_USER, ContentType.APPLICATION_JSON));
        HttpResponse response = client.execute(post);
        int code = response.getStatusLine().getStatusCode();
        String content = EntityUtils.toString(response.getEntity());
        assertEquals(HttpStatus.SC_OK, code);
        assertNotNull(securityService.getUser("user3"));

        // Check returned id
        JsonNode idNode = new ObjectMapper().readTree(content).get("id");
        assertNotNull("Has id field", idNode);
        assertEquals("id is integer", true, idNode.isInt());

        HttpDelete delete = new HttpDelete(getRestURL("/security/users/user3", null, "akiban:topsecret"));
        response = client.execute(delete);
        code = response.getStatusLine().getStatusCode();
        EntityUtils.consume(response.getEntity());
        client.getConnectionManager().shutdown();
        assertEquals(HttpStatus.SC_OK, code);
        assertNull(securityService.getUser("user3"));
    }

    private Connection openPostgresConnection(String user, String password) 
            throws Exception {
        int port = serviceManager().getServiceByClass(com.akiban.sql.pg.PostgresService.class).getPort();
        Class.forName("org.postgresql.Driver");
        String url = String.format("jdbc:postgresql://localhost:%d/%s", port, user);
        return DriverManager.getConnection(url, user, password);
    }

    @Test(expected = SQLException.class)
    public void postgresUnauthenticated() throws Exception {
        openPostgresConnection(null, null).close();
    }

    @Test
    public void postgresAuthenticated() throws Exception {
        Connection conn = openPostgresConnection("user1", "password");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT id FROM utable");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        rs.close();
        stmt.execute("DROP TABLE utable");
        stmt.close();
        conn.close();
    }

    @Test(expected = SQLException.class)
    public void postgresBadUser() throws Exception {
        openPostgresConnection("user2", "whatever").close();
    }

    @Test(expected = SQLException.class)
    public void postgresBadPassword() throws Exception {
        openPostgresConnection("user1", "nope").close();
    }

    @Test(expected = SQLException.class)
    public void postgresWrongSchema() throws Exception {
        Connection conn = openPostgresConnection("user1", "password");
        Statement stmt = conn.createStatement();
        stmt.executeQuery("SELECT id FROM user2.utable");
    }

    @Test(expected = SQLException.class)
    public void postgresWrongSchemaDDL() throws Exception {
        Connection conn = openPostgresConnection("user1", "password");
        Statement stmt = conn.createStatement();
        stmt.executeQuery("DROP TABLE user2.utable");
    }

}
