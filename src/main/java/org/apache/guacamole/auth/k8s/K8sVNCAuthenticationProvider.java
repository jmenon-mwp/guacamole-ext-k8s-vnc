package org.apache.guacamole.auth.k8s;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.UserContext;

/**
 * Authentication provider which provides a dynamic VNC connection to a
 * Kubernetes pod.
 */
public class K8sVNCAuthenticationProvider extends AbstractAuthenticationProvider {

    @Override
    public String getIdentifier() {
        return "k8s-vnc";
    }

    @Override
    public UserContext getUserContext(AuthenticatedUser authenticatedUser)
            throws GuacamoleException {
        return new K8sVNCUserContext(this, authenticatedUser, null);
    }

    @Override
    public UserContext decorate(UserContext context,
            AuthenticatedUser authenticatedUser, Credentials credentials)
            throws GuacamoleException {

        // Don't wrap if already wrapped
        if (context instanceof K8sVNCUserContext)
            return context;

        org.slf4j.LoggerFactory.getLogger(K8sVNCAuthenticationProvider.class)
                .debug("Decorating UserContext for user: {}", authenticatedUser.getIdentifier());
        return new K8sVNCUserContext(this, authenticatedUser, context);
    }

    @Override
    public UserContext redecorate(UserContext decorated, UserContext context,
            AuthenticatedUser authenticatedUser, Credentials credentials)
            throws GuacamoleException {

        // Don't re-wrap if already wrapped
        if (decorated instanceof K8sVNCUserContext)
            return decorated;

        return new K8sVNCUserContext(this, authenticatedUser, decorated);
    }

}
