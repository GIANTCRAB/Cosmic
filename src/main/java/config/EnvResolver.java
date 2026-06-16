package config;

public class EnvResolver {

    private EnvResolver() {
    }

    /**
     * Return the environment value, or null if it is absent or blank.
     * Pass the result of {@code System.getenv(KEY)}; a blank value is treated as unset.
     */
    public static String resolve(String envValue) {
        return (envValue == null || envValue.isBlank()) ? null : envValue;
    }
}
