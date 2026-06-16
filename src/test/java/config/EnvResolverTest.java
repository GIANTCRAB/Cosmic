package config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvResolverTest {

    static Stream<Arguments> resolutionCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", null),
                Arguments.of("   ", null),
                Arguments.of("s3cret", "s3cret")
        );
    }

    @ParameterizedTest
    @MethodSource("resolutionCases")
    void resolvesExpectedValue(String envValue, String expected) {
        assertEquals(expected, EnvResolver.resolve(envValue));
    }
}
