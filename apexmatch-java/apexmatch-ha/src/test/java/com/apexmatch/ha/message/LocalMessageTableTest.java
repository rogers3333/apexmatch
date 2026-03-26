package com.apexmatch.ha.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LocalMessageTableTest {

    private LocalMessageTable table;

    @BeforeEach
    void setUp() {
        table = new LocalMessageTable();
    }

    @Test
    void saveAndGet() {
        LocalMessage msg = table.save("trade", "{\"tradeId\":1}");
        assertThat(msg.getStatus()).isEqualTo(LocalMessage.Status.PENDING);
        assertThat(table.size()).isEqualTo(1);
    }

    @Test
    void scanSendsMessages() {
        table.save("trade", "{\"tradeId\":1}");
        table.save("trade", "{\"tradeId\":2}");

        List<String> sent = new ArrayList<>();
        int count = table.scan(m -> sent.add(m.getPayload()));

        assertThat(count).isEqualTo(2);
        assertThat(sent).hasSize(2);
        assertThat(table.getPending()).isEmpty();
    }

    @Test
    void scanRetriesOnFailure() {
        LocalMessage msg = table.save("trade", "{\"tradeId\":1}");

        table.scan(m -> { throw new RuntimeException("Kafka down"); });
        assertThat(msg.getRetryCount()).isEqualTo(1);
        assertThat(msg.getStatus()).isEqualTo(LocalMessage.Status.PENDING);

        table.scan(m -> {});
        assertThat(msg.getStatus()).isEqualTo(LocalMessage.Status.SENT);
    }

    @Test
    void scanMarksFailedAfterMaxRetry() {
        LocalMessage msg = table.save("trade", "{\"tradeId\":1}");

        for (int i = 0; i < 5; i++) {
            table.scan(m -> { throw new RuntimeException("fail"); });
        }
        table.scan(m -> {});

        assertThat(msg.getStatus()).isEqualTo(LocalMessage.Status.FAILED);
    }

    @Test
    void confirmMessage() {
        LocalMessage msg = table.save("trade", "{}");
        table.scan(m -> {});
        table.confirm(msg.getMessageId());
        assertThat(table.get(msg.getMessageId()).getStatus()).isEqualTo(LocalMessage.Status.CONFIRMED);
    }
}
