package org.apache.guacamole.auth.k8s.connection;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

import io.kubernetes.client.custom.Quantity;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleServerException;
import org.apache.guacamole.net.GuacamoleSocket;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.net.InetGuacamoleSocket;
import org.apache.guacamole.net.SimpleGuacamoleTunnel;
import org.apache.guacamole.net.auth.Connection;
import org.apache.guacamole.net.auth.GuacamoleProxyConfiguration;
import org.apache.guacamole.protocol.ConfiguredGuacamoleSocket;
import org.apache.guacamole.protocol.GuacamoleClientInformation;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.guacamole.net.auth.DelegatingConnection;

import org.apache.guacamole.auth.k8s.K8sVNCProperties;
import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.environment.LocalEnvironment;

public class K8sVNCConnection extends DelegatingConnection {

    private static final Logger logger = LoggerFactory.getLogger(K8sVNCConnection.class);

    private final String userId;

    /**
     * Constructor for wrapping an existing connection (e.g. from Admin UI).
     */
    public K8sVNCConnection(Connection connection, String userId) {
        super(connection);
        this.userId = userId;
    }

    /**
     * Returns the underlying connection being wrapped.
     * 
     * @return
     *         The underlying connection.
     */
    public Connection getDelegate() {
        return getDelegateConnection();
    }

    @Override
    public Map<String, String> getAttributes() {

        // Get existing attributes
        Map<String, String> attributes = new HashMap<>(super.getAttributes());

        // Mirror K8s parameters into attributes if they don't exist
        GuacamoleConfiguration config = getConfiguration();
        if (config != null) {
            String[] k8sParams = { "k8s-vnc-enabled", "k8s-vnc-namespace", "k8s-vnc-cpu", "k8s-vnc-memory",
                    "k8s-vnc-pvc-size", "k8s-vnc-image", "k8s-vnc-endpoint", "k8s-vnc-skip-tls-verify" };
            for (String param : k8sParams) {
                String value = config.getParameter(param);
                if (value != null) {

                    attributes.put(param, value);
                }
            }
        }

        return attributes;

    }

    @Override
    public void setAttributes(Map<String, String> attributes) {

        // Set attributes normally
        super.setAttributes(attributes);

        // Also mirror specific K8s attributes into parameters for persistence
        GuacamoleConfiguration config = getConfiguration();
        if (config != null && attributes != null) {

            String[] k8sParams = { "k8s-vnc-enabled", "k8s-vnc-namespace", "k8s-vnc-cpu", "k8s-vnc-memory",
                    "k8s-vnc-pvc-size", "k8s-vnc-image", "k8s-vnc-endpoint", "k8s-vnc-skip-tls-verify" };

            for (String param : k8sParams) {
                String value = attributes.get(param);

                if (value != null) {

                    config.setParameter(param, value);
                }
            }

            // Force the underlying provider to save the updated configuration
            setConfiguration(config);
        }

    }

    @Override
    public GuacamoleTunnel connect(GuacamoleClientInformation info, Map<String, String> tokens)
            throws GuacamoleException {

        GuacamoleConfiguration connectionConfig = getConfiguration();
        Map<String, String> params = connectionConfig.getParameters();

        logger.info("K8sVNCConnection.connect() called. Name: '{}', Protocol: '{}'",
                getName(), connectionConfig.getProtocol());

        Environment environment = LocalEnvironment.getInstance();

        // Identify if this is a Kubernetes-managed connection
        Map<String, String> attributes = getAttributes();

        // Check both attributes and parameters for enablement
        boolean isK8sEnabled = "true".equalsIgnoreCase(attributes.get("k8s-vnc-enabled"))
                || "true".equalsIgnoreCase(params.get("k8s-vnc-enabled"));

        // If not explicitly enabled, just connect normally
        if (!isK8sEnabled) {
            logger.info("Standard VNC connection detected (Kubernetes provisioning not enabled).");
            GuacamoleProxyConfiguration proxyConfig = environment.getDefaultGuacamoleProxyConfiguration();
            GuacamoleSocket socket = new ConfiguredGuacamoleSocket(
                    new InetGuacamoleSocket(proxyConfig.getHostname(), proxyConfig.getPort()),
                    connectionConfig,
                    info);
            return new SimpleGuacamoleTunnel(socket);
        }

        logger.info("Connecting to Kubernetes VNC Pod for user: {}", userId);

        try {
            // 1. Initial configuration from parameters/properties
            String namespace = getParam(connectionConfig, "k8s-namespace",
                    getProperty(environment, K8sVNCProperties.K8S_VNC_NAMESPACE, "default"));

            String vncPassword = getParam(connectionConfig, "vnc-password",
                    getProperty(environment, K8sVNCProperties.K8S_VNC_PASSWORD, null));

            String image = getParam(connectionConfig, "k8s-vnc-image",
                    getProperty(environment, K8sVNCProperties.K8S_VNC_IMAGE, "guacamole-k8s-vnc:latest"));

            String pvcSize = getParam(connectionConfig, "k8s-pvc-size",
                    getProperty(environment, K8sVNCProperties.K8S_VNC_PVC_SIZE, "1Gi"));

            String apiEndpoint = getParam(connectionConfig, "k8s-vnc-endpoint",
                    getProperty(environment, K8sVNCProperties.K8S_VNC_ENDPOINT, null));

            boolean skipTls = "true".equalsIgnoreCase(getParam(connectionConfig, "k8s-vnc-skip-tls-verify",
                    getProperty(environment, K8sVNCProperties.K8S_VNC_SKIP_TLS_VERIFY, false).toString()));

            String cpu = "1";
            String memory = "2Gi";

            String vncPortString = getParam(connectionConfig, "vnc-port", "5901");
            int vncPort = Integer.parseInt(vncPortString);

            // 2. Resolve parameters from Connection Attributes
            logger.info("Using Kubernetes parameters from Connection Attributes.");
            namespace = attributes.getOrDefault("k8s-vnc-namespace", namespace);
            cpu = attributes.getOrDefault("k8s-vnc-cpu", cpu);
            memory = attributes.getOrDefault("k8s-vnc-memory", memory);
            pvcSize = attributes.getOrDefault("k8s-vnc-pvc-size", pvcSize);
            image = attributes.getOrDefault("k8s-vnc-image", image);
            apiEndpoint = attributes.getOrDefault("k8s-vnc-endpoint", apiEndpoint);
            String skipTlsAttr = attributes.get("k8s-vnc-skip-tls-verify");
            if (skipTlsAttr != null) {
                skipTls = "true".equalsIgnoreCase(skipTlsAttr);
            }

            // Syntax validation for common parameters
            if (!namespace.matches("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$"))
                throw new GuacamoleServerException("Invalid Kubernetes Namespace: " + namespace);
            if (!cpu.matches("^[0-9]+m?$|^[0-9]+(\\.[0-9]+)?$"))
                throw new GuacamoleServerException("Invalid CPU requirement: " + cpu);
            if (!memory.matches("^[0-9]+[KMGTPE]i?$"))
                throw new GuacamoleServerException("Invalid Memory requirement: " + memory);
            if (!pvcSize.matches("^[0-9]+[KMGTPE]i?$"))
                throw new GuacamoleServerException("Invalid PVC size: " + pvcSize);

            logger.info("Final provisioning parameters: namespace={}, cpu={}, memory={}, pvc={}",
                    namespace, cpu, memory, pvcSize);

            logger.info(
                    "Connection parameters: namespace={}, image={}, pvcSize={}, vncPort={}, passwordSet={}, endpoint={}, cpu={}, memory={}",
                    namespace, image, pvcSize, vncPort, (vncPassword != null), apiEndpoint, cpu, memory);

            if (vncPassword == null || vncPassword.isEmpty())
                throw new GuacamoleServerException("VNC Password must be configured (k8s-vnc-password).");

            if (image == null || image.isEmpty())
                throw new GuacamoleServerException("Container Image must be configured (k8s-vnc-image).");

            GuacamoleProxyConfiguration proxyConfig = environment.getDefaultGuacamoleProxyConfiguration();

            // Initialize Kubernetes Client
            ApiClient client = Config.defaultClient();
            if (apiEndpoint != null && !apiEndpoint.isEmpty()) {
                logger.info("Using Kubernetes API endpoint: {} (skipTls={})", apiEndpoint, skipTls);
                client.setBasePath(apiEndpoint);
            }
            try {
                client.setVerifyingSsl(!skipTls);
            } catch (Exception e) {
                logger.warn("Failed to set SSL verification to {}: {}. Forcing skip-tls.", !skipTls, e.getMessage());
                client.setVerifyingSsl(false);
            }
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();

            String sanitizedUserId = userId.toLowerCase().replaceAll("[^a-z0-9]", "-");
            // Use the connection identifier (unique per Guacamole connection) to make the
            // pod name unique per connection
            String connId = getIdentifier();
            String sanitizedConnId = connId != null ? connId.toLowerCase().replaceAll("[^a-z0-9]", "-") : "default";
            String podName = "vnc-" + sanitizedUserId + "-" + sanitizedConnId;
            String pvcName = "pvc-" + sanitizedUserId + "-" + sanitizedConnId;

            // 3. Ensure PVC exists
            ensurePvcExists(api, namespace, pvcName, pvcSize, podName);

            // 4. Ensure Pod exists
            ensurePodExists(api, namespace, podName, pvcName, vncPassword, image, vncPortString, sanitizedUserId, cpu,
                    memory);

            // 4. Wait for Pod IP
            String podIp = waitForPodIp(api, namespace, podName);
            logger.info("Pod {} is ready at IP {}. Waiting for VNC port {}...", podName, podIp, vncPort);

            // 5. Wait for VNC port to be open
            boolean portOpen = false;
            for (int i = 0; i < 30; i++) {
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress(podIp, vncPort), 1000);
                    portOpen = true;
                    break;
                } catch (java.io.IOException e) {
                    Thread.sleep(1000);
                }
            }

            if (!portOpen)
                throw new GuacamoleServerException("VNC server on pod " + podName + " failed to start in time.");

            logger.info("VNC port {} is open on {}. Connecting...", vncPort, podIp);

            // 6. Connect via guacd
            GuacamoleConfiguration config = new GuacamoleConfiguration();
            config.setProtocol("vnc");
            config.setParameter("hostname", podIp);
            config.setParameter("port", vncPortString);
            config.setParameter("password", vncPassword);

            GuacamoleSocket socket = new ConfiguredGuacamoleSocket(
                    new InetGuacamoleSocket(proxyConfig.getHostname(), proxyConfig.getPort()),
                    config,
                    info);

            return new SimpleGuacamoleTunnel(socket);

        } catch (Exception e) {
            logger.error("Failed to establish Kubernetes VNC connection", e);
            if (e instanceof GuacamoleException)
                throw (GuacamoleException) e;
            throw new GuacamoleServerException("Error provisioning or connecting to pod: " + e.getMessage());
        }
    }

    private String getParam(GuacamoleConfiguration config, String name, String defaultValue) {
        if (config == null)
            return defaultValue;
        String val = config.getParameter(name);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    private <T> T getProperty(org.apache.guacamole.environment.Environment environment,
            org.apache.guacamole.properties.GuacamoleProperty<T> prop, T defaultValue)
            throws GuacamoleException {
        T val = environment.getProperty(prop);
        if (val != null)
            return val;

        // Fallback to environment variable (e.g. k8s-vnc-password -> K8S_VNC_PASSWORD)
        String envName = prop.getName().toUpperCase().replace('-', '_');
        String envVal = System.getenv(envName);
        if (envVal != null && !envVal.isEmpty()) {
            if (prop instanceof org.apache.guacamole.properties.BooleanGuacamoleProperty)
                return prop.parseValue(envVal);
            return prop.parseValue(envVal);
        }

        return defaultValue;
    }

    private void ensurePvcExists(CoreV1Api api, String namespace, String pvcName, String pvcSize, String podName)
            throws Exception {
        try {
            V1PersistentVolumeClaim pvc = api.readNamespacedPersistentVolumeClaim(pvcName, namespace).execute();

            // Check if expansion is needed
            Quantity currentSize = pvc.getSpec().getResources().getRequests().get("storage");
            Quantity newSize = new Quantity(pvcSize);

            // Compare values (using big decimal for safety)
            BigDecimal currentBytes = currentSize.getNumber();
            BigDecimal newBytes = newSize.getNumber();

            if (newBytes.compareTo(currentBytes) > 0) {
                logger.info("Expanding PVC {} from {} to {}", pvcName, currentSize.toSuffixedString(), pvcSize);

                // 1. Delete Pod first (User Request / Best Practice for Resize)
                try {
                    logger.info("Deleting pod {} to facilitate PVC expansion.", podName);
                    api.deleteNamespacedPod(podName, namespace).execute();

                    // Wait for termination
                    logger.info("Waiting for pod {} to terminate...", podName);
                    for (int i = 0; i < 30; i++) {
                        try {
                            api.readNamespacedPod(podName, namespace).execute();
                            Thread.sleep(1000);
                        } catch (io.kubernetes.client.openapi.ApiException e) {
                            if (e.getCode() == 404)
                                break;
                        }
                    }
                } catch (io.kubernetes.client.openapi.ApiException e) {
                    if (e.getCode() != 404)
                        throw e;
                }

                try {
                    // 2. Resize using REPLACE (PUT) to avoid 415 Media Type issues
                    pvc.getSpec().getResources().setRequests(Collections.singletonMap("storage", newSize));
                    api.replaceNamespacedPersistentVolumeClaim(pvcName, namespace, pvc).execute();
                } catch (io.kubernetes.client.openapi.ApiException e) {
                    if (e.getCode() == 422 || e.getCode() == 403) {
                        logger.warn(
                                "PVC expansion failed (not supported by StorageClass?): {}. Proceeding with existing size.",
                                e.getMessage());
                    } else {
                        throw e;
                    }
                }
            }

        } catch (io.kubernetes.client.openapi.ApiException e) {
            if (e.getCode() == 404) {
                V1PersistentVolumeClaim newPvc = new V1PersistentVolumeClaim()
                        .metadata(new V1ObjectMeta().name(pvcName))
                        .spec(new V1PersistentVolumeClaimSpec()
                                .accessModes(Collections.singletonList("ReadWriteOnce"))
                                .resources(new V1VolumeResourceRequirements().requests(Collections
                                        .singletonMap("storage", new io.kubernetes.client.custom.Quantity(pvcSize)))));
                api.createNamespacedPersistentVolumeClaim(namespace, newPvc).execute();
            } else {
                throw e;
            }
        }
    }

    private V1Pod ensurePodExists(CoreV1Api api, String namespace, String podName, String pvcName,
            String password, String image, String vncPort, String containerUser, String cpu, String memory)
            throws Exception {
        try {
            V1Pod existingPod = api.readNamespacedPod(podName, namespace).execute();

            // Check if resources match
            boolean needsRecreation = false;
            V1Container container = existingPod.getSpec().getContainers().get(0);

            // Check CPU
            if (cpu != null && !cpu.isEmpty()) {
                Quantity currentCpu = container.getResources().getLimits().get("cpu");
                Quantity requestedCpu = new Quantity(cpu);
                if (currentCpu == null || currentCpu.getNumber().compareTo(requestedCpu.getNumber()) != 0) {
                    logger.info("Pod CPU mismatch (Current: {}, Requested: {}). Recreating...", currentCpu, cpu);
                    needsRecreation = true;
                }
            }

            // Check Memory
            if (!needsRecreation && memory != null && !memory.isEmpty()) {
                Quantity currentMem = container.getResources().getLimits().get("memory");
                Quantity requestedMem = new Quantity(memory);
                if (currentMem == null || currentMem.getNumber().compareTo(requestedMem.getNumber()) != 0) {
                    logger.info("Pod Memory mismatch (Current: {}, Requested: {}). Recreating...", currentMem, memory);
                    needsRecreation = true;
                }
            }

            if (needsRecreation) {
                logger.info("Deleting pod {} to apply new resource limits.", podName);
                api.deleteNamespacedPod(podName, namespace).execute();

                // Wait for deletion
                logger.info("Waiting for pod {} to terminate...", podName);
                for (int i = 0; i < 30; i++) {
                    try {
                        api.readNamespacedPod(podName, namespace).execute();
                        Thread.sleep(1000);
                    } catch (io.kubernetes.client.openapi.ApiException e) {
                        if (e.getCode() == 404)
                            break; // Gone
                    }
                }
            } else {
                return existingPod;
            }

        } catch (io.kubernetes.client.openapi.ApiException e) {
            if (e.getCode() != 404)
                throw e;
        }

        // Pod creation logic (reached if 404 or if we deleted it)
        String containerUid = "1000";

        logger.info("Creating Pod {} with image {} (CPU: {}, Mem: {})", podName, image, cpu, memory);

        V1ResourceRequirements resources = new V1ResourceRequirements();
        if (cpu != null && !cpu.isEmpty()) {
            resources.putRequestsItem("cpu", new io.kubernetes.client.custom.Quantity(cpu));
            resources.putLimitsItem("cpu", new io.kubernetes.client.custom.Quantity(cpu));
        }
        if (memory != null && !memory.isEmpty()) {
            resources.putRequestsItem("memory", new io.kubernetes.client.custom.Quantity(memory));
            resources.putLimitsItem("memory", new io.kubernetes.client.custom.Quantity(memory));
        }

        V1Pod pod = new V1Pod()
                .apiVersion("v1")
                .kind("Pod")
                .metadata(new V1ObjectMeta().name(podName)
                        .labels(Collections.singletonMap("user", userId)))
                .spec(new V1PodSpec()
                        .restartPolicy("Always")
                        .overhead(null)
                        .runtimeClassName(null)
                        .securityContext(new V1PodSecurityContext()
                                .fsGroup(1000L))
                        .containers(Collections.singletonList(
                                new V1Container()
                                        .name("vnc-container")
                                        .image(image)
                                        .imagePullPolicy("IfNotPresent")
                                        .resources(resources)
                                        .addPortsItem(
                                                new V1ContainerPort().containerPort(Integer.parseInt(vncPort)))
                                        .env(java.util.Arrays.asList(
                                                new V1EnvVar().name("VNC_PW").value(password),
                                                new V1EnvVar().name("VNC_PASSWORD").value(password),
                                                new V1EnvVar().name("VNC_USER").value(containerUser),
                                                new V1EnvVar().name("VNC_UID").value(containerUid),
                                                new V1EnvVar().name("VNC_GID").value(containerUid),
                                                new V1EnvVar().name("VNC_RESOLUTION").value("1280x800"),
                                                new V1EnvVar().name("VNC_COL_DEPTH").value("24"),
                                                new V1EnvVar().name("HOME").value("/home/" + containerUser)))
                                        .volumeMounts(Collections.singletonList(
                                                new V1VolumeMount().name("home-dir")
                                                        .mountPath("/home/" + containerUser)))))
                        .volumes(Collections.singletonList(
                                new V1Volume().name("home-dir").persistentVolumeClaim(
                                        new V1PersistentVolumeClaimVolumeSource().claimName(pvcName)))));
        return api.createNamespacedPod(namespace, pod).execute();
    }

    private String waitForPodIp(CoreV1Api api, String namespace, String podName) throws Exception {
        for (int i = 0; i < 60; i++) {
            V1Pod pod = api.readNamespacedPod(podName, namespace).execute();
            String ip = pod.getStatus().getPodIP();
            if (ip != null && !ip.isEmpty() && "Running".equals(pod.getStatus().getPhase())) {
                return ip;
            }
            Thread.sleep(2000);
        }
        throw new Exception("Timeout waiting for Pod IP");
    }

    @Override
    public int getActiveConnections() {
        return 0;
    }

    @Override
    public Date getLastActive() {
        return null;
    }

}
