package com.finance.instrument_price_store.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Buffer for an in-progress batch. It keeps only the best (latest asOf) record per id.
 */
public class BatchBuffer {
	private final Map<String, PriceRecord> latestRecords = new HashMap<>();
	private volatile boolean finalized = false;


	public synchronized void addChunk(List<PriceRecord> chunk) {
		Objects.requireNonNull(chunk, "chunk");
		for (PriceRecord r : chunk) {
			String id = r.getId();
			PriceRecord existing = latestRecords.get(id);
			if (existing == null || r.getAsOf().isAfter(existing.getAsOf())) {
				latestRecords.put(id, r);
			}
		}
	}


	public synchronized Map<String, PriceRecord> snapshotAndFinalize() {
		this.finalized = true;
		return Collections.unmodifiableMap(new HashMap<>(latestRecords));
	}


	public boolean isFinalized() {
		return finalized;
	}
}
