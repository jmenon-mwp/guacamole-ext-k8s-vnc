package org.apache.guacamole.auth.k8s;

import java.util.Collection;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.auth.k8s.connection.K8sVNCConnection;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.AuthenticationProvider;
import org.apache.guacamole.net.auth.Connection;
import org.apache.guacamole.net.auth.Directory;
import org.apache.guacamole.net.auth.UserContext;
import org.apache.guacamole.net.auth.DelegatingUserContext;
import org.apache.guacamole.net.auth.DelegatingDirectory;
import org.apache.guacamole.form.Field;
import org.apache.guacamole.form.Form;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;

/**
 * UserContext implementation which provides and intercepts Kubernetes VNC
 * connections.
 */
public class K8sVNCUserContext extends DelegatingUserContext {

    private static final Logger logger = LoggerFactory.getLogger(K8sVNCUserContext.class);

    private final AuthenticationProvider authProvider;
    private final AuthenticatedUser authenticatedUser;

    public K8sVNCUserContext(AuthenticationProvider authProvider,
            AuthenticatedUser authenticatedUser, UserContext delegate) {
        super(delegate != null ? delegate
                : new org.apache.guacamole.net.auth.simple.SimpleUserContext(authProvider,
                        authenticatedUser.getIdentifier(), Collections.emptyMap()));
        this.authProvider = authProvider;
        this.authenticatedUser = authenticatedUser;
        if (logger.isDebugEnabled())
            logger.debug("Initialized K8sVNCUserContext for user {}", authenticatedUser.getIdentifier());
    }

    @Override
    public Directory<Connection> getConnectionDirectory() throws GuacamoleException {
        logger.debug("Fetching connection directory for user {}", authenticatedUser.getIdentifier());
        return new K8sConnectionDirectory(super.getConnectionDirectory());
    }

    @Override
    public Collection<Form> getConnectionAttributes() {
        Collection<Form> attributes = new ArrayList<>(super.getConnectionAttributes());

        // Remove any existing provisioning form to ensure the latest schema (including
        // new fields) is used
        attributes.removeIf(form -> "K8S_POD_PROVISIONING".equalsIgnoreCase(form.getName()));

        logger.debug("Injecting Kubernetes Pod Provisioning schema into connection attributes.");
        attributes.add(new Form("K8S_POD_PROVISIONING",
                java.util.Arrays.asList(
                        new Field("k8s-vnc-enabled", Field.Type.ENUM, java.util.Arrays.asList("true", "false")),
                        new Field("k8s-vnc-namespace", Field.Type.TEXT),
                        new Field("k8s-vnc-cpu", Field.Type.TEXT),
                        new Field("k8s-vnc-memory", Field.Type.TEXT),
                        new Field("k8s-vnc-pvc-size", Field.Type.TEXT),
                        new Field("k8s-vnc-image", Field.Type.TEXT),
                        new Field("k8s-vnc-endpoint", Field.Type.TEXT),
                        new Field("k8s-vnc-skip-tls-verify", Field.Type.ENUM,
                                java.util.Arrays.asList("true", "false")))));
        return attributes;
    }

    private class K8sConnectionDirectory extends DelegatingDirectory<Connection> {

        public K8sConnectionDirectory(Directory<Connection> directory) {
            super(directory);
        }

        @Override
        public Connection get(String identifier) throws GuacamoleException {
            Connection connection = super.get(identifier);
            if (connection != null && connection.getConfiguration() != null) {
                String protocol = connection.getConfiguration().getProtocol();
                if ("vnc".equals(protocol)) {
                    return new K8sVNCConnection(connection, authenticatedUser.getIdentifier());
                }
            }
            return connection;
        }

        @Override
        public void update(Connection connection) throws GuacamoleException {
            if (connection instanceof K8sVNCConnection) {
                K8sVNCConnection k8sConn = (K8sVNCConnection) connection;
                // Shadowing is already handled by setAttributes in the wrapper
                super.update(k8sConn.getDelegate());
            } else {
                super.update(connection);
            }
        }

        @Override
        public void add(Connection connection) throws GuacamoleException {
            if (connection instanceof K8sVNCConnection) {
                K8sVNCConnection k8sConn = (K8sVNCConnection) connection;
                super.add(k8sConn.getDelegate());
            } else {
                super.add(connection);
            }
        }

        @Override
        public java.util.Set<String> getIdentifiers() throws GuacamoleException {
            return super.getIdentifiers();
        }

        @Override
        public Collection<Connection> getAll(Collection<String> identifiers) throws GuacamoleException {
            Collection<Connection> connections = new java.util.ArrayList<>();
            for (String id : identifiers) {
                Connection conn = get(id);
                if (conn != null)
                    connections.add(conn);
            }
            return connections;
        }
    }

    @Override
    public AuthenticationProvider getAuthenticationProvider() {
        return authProvider;
    }

}
