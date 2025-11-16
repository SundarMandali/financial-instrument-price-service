package com.finance.instrument_price_store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.finance.instrument_price_store.exception.BatchAlreadyExistsException;
import com.finance.instrument_price_store.exception.BatchNotFoundException;
import com.finance.instrument_price_store.exception.ValidationException;
import com.finance.instrument_price_store.impl.LastValuePriceServiceImpl;
import com.finance.instrument_price_store.model.PriceRecord;

import static org.junit.jupiter.api.Assertions.*;

public class LastValuePriceServiceImplTest {

    private LastValuePriceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LastValuePriceServiceImpl();
    }

    @Test
    void startBatch_duplicate_throwsBatchAlreadyExistsException() {
        service.startBatch("batch1");
        assertThrows(BatchAlreadyExistsException.class, () -> service.startBatch("batch1"));
    }

    @Test
    void uploadChunk_missingBatch_throwsBatchNotFoundException() {
        PriceRecord r = new PriceRecord("A", Instant.now(), Map.of("price", 1));
        assertThrows(BatchNotFoundException.class, () -> service.uploadChunk("no", List.of(r)));
    }

    @Test
    void uploadChunk_chunkTooLarge_throwsValidationException() {
        service.startBatch("b1");
        List<PriceRecord> big = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            big.add(new PriceRecord("id" + i, Instant.now(), Map.of("p", i)));
        }
        assertThrows(ValidationException.class, () -> service.uploadChunk("b1", big));
    }

    @Test
    void completeBatch_missingBatch_throwsBatchNotFoundException() {
        assertThrows(BatchNotFoundException.class, () -> service.completeBatch("noSuch"));
    }

    @Test
    void cancelBatch_missingBatch_isIdempotent_noException() {
        assertDoesNotThrow(() -> service.cancelBatch("noSuch"));
    }

    @Test
    void completeBatch_commitsRecords_visibleToConsumers() {
        service.startBatch("b1");
        PriceRecord r1 = new PriceRecord("AAPL", Instant.parse("2025-11-09T10:00:00Z"), Map.of("price", 190));
        PriceRecord r2 = new PriceRecord("GOOG", Instant.parse("2025-11-09T09:00:00Z"), Map.of("price", 1200));
        service.uploadChunk("b1", List.of(r1, r2));

        service.completeBatch("b1");

        Optional<PriceRecord> aapl = service.getLastPrice("AAPL");
        Optional<PriceRecord> goog = service.getLastPrice("GOOG");

        assertTrue(aapl.isPresent());
        assertEquals(190, aapl.get().getPayload().get("price"));
        assertTrue(goog.isPresent());
        assertEquals(1200, goog.get().getPayload().get("price"));
    }

    @Test
    void cancelBatch_discardsRecords_notVisible() {
        service.startBatch("b2");
        PriceRecord r = new PriceRecord("TSLA", Instant.now(), Map.of("price", 900));
        service.uploadChunk("b2", List.of(r));
        service.cancelBatch("b2");

        Optional<PriceRecord> maybe = service.getLastPrice("TSLA");
        assertTrue(maybe.isEmpty());
    }

    @Test
    void latestAsOfWins_withinSameBatch() {
        service.startBatch("b1");
        PriceRecord older = new PriceRecord("X", Instant.parse("2025-01-01T10:00:00Z"), Map.of("price", 50));
        PriceRecord newer = new PriceRecord("X", Instant.parse("2025-01-01T12:00:00Z"), Map.of("price", 100));
        service.uploadChunk("b1", List.of(older, newer));
        service.completeBatch("b1");

        Optional<PriceRecord> res = service.getLastPrice("X");
        assertTrue(res.isPresent());
        assertEquals(100, res.get().getPayload().get("price"));
    }

    @Test
    void tieBreaker_payloadString_compare_deterministic() {
        service.startBatch("b1");
        PriceRecord r1 = new PriceRecord("T", Instant.parse("2025-01-01T10:00:00Z"), Map.of("p", "A"));
        PriceRecord r2 = new PriceRecord("T", Instant.parse("2025-01-01T10:00:00Z"), Map.of("p", "B"));
        service.uploadChunk("b1", List.of(r1, r2));
        service.completeBatch("b1");

        Optional<PriceRecord> res = service.getLastPrice("T");
        assertTrue(res.isPresent());
        assertEquals("B", res.get().getPayload().get("p")); // "B" > "A" lexicographically
    }
}
