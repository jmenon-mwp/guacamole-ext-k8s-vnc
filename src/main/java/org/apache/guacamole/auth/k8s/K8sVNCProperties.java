package org.apache.guacamole.auth.k8s;

import org.apache.guacamole.properties.BooleanGuacamoleProperty;
import org.apache.guacamole.properties.StringGuacamoleProperty;

/**
 * Properties for the Kubernetes VNC extension.
 */
public class K8sVNCProperties {

    public static final StringGuacamoleProperty K8S_VNC_NAMESPACE = new StringGuacamoleProperty() {
        @Override
        public String getName() {
            return "k8s-vnc-namespace";
        }
    };

    public static final StringGuacamoleProperty K8S_VNC_PASSWORD = new StringGuacamoleProperty() {
        @Override
        public String getName() {
            return "k8s-vnc-password";
        }
    };

    public static final StringGuacamoleProperty K8S_VNC_IMAGE = new StringGuacamoleProperty() {
        @Override
        public String getName() {
            return "k8s-vnc-image";
        }
    };

    public static final StringGuacamoleProperty K8S_VNC_PVC_SIZE = new StringGuacamoleProperty() {
        @Override
        public String getName() {
            return "k8s-vnc-pvc-size";
        }
    };

    public static final StringGuacamoleProperty K8S_VNC_ENDPOINT = new StringGuacamoleProperty() {
        @Override
        public String getName() {
            return "k8s-vnc-endpoint";
        }
    };

    public static final BooleanGuacamoleProperty K8S_VNC_SKIP_TLS_VERIFY = new BooleanGuacamoleProperty() {
        @Override
        public String getName() {
            return "k8s-vnc-skip-tls-verify";
        }
    };

}
