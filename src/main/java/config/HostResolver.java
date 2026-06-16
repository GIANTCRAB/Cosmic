package config;

public class HostResolver {

    private HostResolver() {
    }

    public static String resolve(String envHost, String configHost) {
        return envHost != null ? envHost : configHost;
    }
}
