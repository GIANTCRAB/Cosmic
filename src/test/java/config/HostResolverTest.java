package config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostResolverTest {

    @Test
    void resolvesConfigHostWhenEnvAbsent() {
        assertEquals("127.0.0.1", HostResolver.resolve(null, "127.0.0.1"));
    }

    @Test
    void resolvesEnvHostWhenPresent() {
        assertEquals("203.0.113.10", HostResolver.resolve("203.0.113.10", "127.0.0.1"));
    }

    static Stream<Arguments> resolutionCases() {
        return Stream.of(
                Arguments.of(null, "127.0.0.1", "127.0.0.1"),
                Arguments.of("203.0.113.10", "127.0.0.1", "203.0.113.10"),
                Arguments.of("1.2.3.4", null, "1.2.3.4")
        );
    }

    @ParameterizedTest
    @MethodSource("resolutionCases")
    void resolvesExpectedHost(String envHost, String configHost, String expected) {
        assertEquals(expected, HostResolver.resolve(envHost, configHost));
    }
}
