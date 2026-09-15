package com.projectmodule.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    @DisplayName("carries the version 7 marker")
    void hasVersionSeven() {
        assertThat(UuidV7.generate().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("carries the RFC 4122 variant")
    void hasRfcVariant() {
        assertThat(UuidV7.generate().variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("embeds the supplied timestamp")
    void embedsTimestamp() {
        Instant instant = Instant.ofEpochMilli(1_700_000_000_000L);

        assertThat(UuidV7.timestampOf(UuidV7.generate(instant)))
                .isEqualTo(instant.toEpochMilli());
    }

    @Test
    @DisplayName("orders by time, which is the point of choosing version 7")
    void ordersByTime() {
        Instant earlier = Instant.ofEpochMilli(1_700_000_000_000L);
        Instant later = earlier.plusMillis(1);

        assertThat(UuidV7.timestampOf(UuidV7.generate(earlier)))
                .isLessThan(UuidV7.timestampOf(UuidV7.generate(later)));
    }

    @Test
    @DisplayName("does not collide within the same millisecond")
    void isUniqueWithinOneMillisecond() {
        Instant fixed = Instant.ofEpochMilli(1_700_000_000_000L);
        Set<UUID> generated = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            generated.add(UuidV7.generate(fixed));
        }

        assertThat(generated).hasSize(10_000);
    }

    @Test
    @DisplayName("refuses to read a timestamp from a non-version-7 identifier")
    void rejectsForeignUuid() {
        UUID version4 = UUID.randomUUID();

        assertThatThrownBy(() -> UuidV7.timestampOf(version4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
