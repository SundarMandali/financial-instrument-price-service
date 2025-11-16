package com.finance.instrument_price_store.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.finance.instrument_price_store.api.LastValuePriceService;
import com.finance.instrument_price_store.exception.BatchAlreadyExistsException;
import com.finance.instrument_price_store.exception.BatchNotFoundException;
import com.finance.instrument_price_store.exception.InvalidBatchStateException;
import com.finance.instrument_price_store.exception.ValidationException;
import com.finance.instrument_price_store.model.BatchBuffer;
import com.finance.instrument_price_store.model.PriceRecord;

/**
 * Thread-safe implementation of LastValuePriceService.
 *
 * Design summary:
 * - In-progress batches are stored in a ConcurrentHashMap<String, BatchBuffer>.
 * - Live (committed) view is stored as an AtomicReference to an immutable Map<String, PriceRecord>.
 * - On completeBatch: build a delta (best per id in the batch) and apply it by creating a new map
 *   (copy of previous live map + delta applied) and CAS it into liveRef. This provides atomic,
 *   whole-batch visibility: readers see either the state before or after the commit, never a partial state.
 */
@Service
public class LastValuePriceServiceImpl implements LastValuePriceService {

	private static final int MAX_CHUNK_SIZE = 1000;

	/**
	 * Tracks in-progress batches. Each batchId -> BatchBuffer.
	 * ConcurrentHashMap provides thread-safety for producers operating concurrently.
	 */
	private final ConcurrentHashMap<String, BatchBuffer> batches = new ConcurrentHashMap<>();

	/**
	 * The live, committed view. Readers access this map without locks via liveRef.get().
	 * On commit we replace the map with a new unmodifiable instance using compareAndSet for atomicity.
	 */
	private final AtomicReference<Map<String, PriceRecord>> liveRef =
			new AtomicReference<>(Collections.emptyMap());

	@Override
	public void startBatch(String batchId) {
		validateBatchId(batchId);
		BatchBuffer prev = batches.putIfAbsent(batchId, new BatchBuffer());
		if (prev != null) {
			throw new BatchAlreadyExistsException(batchId);
		}
	}

	@Override
	public void uploadChunk(String batchId, List<PriceRecord> chunk) {
		validateBatchId(batchId);
		Objects.requireNonNull(chunk, "chunk");

		if (chunk.size() > MAX_CHUNK_SIZE) {
			throw new ValidationException("chunk size > " + MAX_CHUNK_SIZE);
		}

		BatchBuffer buffer = batches.get(batchId);
		if (buffer == null) {
			throw new BatchNotFoundException(batchId);
		}
		if (buffer.isFinalized()) {
			throw new InvalidBatchStateException("Batch already completed or cancelled: " + batchId);
		}

		// Validate records 
		for (PriceRecord r : chunk) {
			validateRecord(r);
		}

		// Add chunk to buffer. BatchBuffer internally keeps only the best (latest asOf) per id.
		buffer.addChunk(chunk);
	}

	@Override
	public void completeBatch(String batchId) {
		validateBatchId(batchId);

		// Remove the buffer from in-progress map; if missing -> not found
		BatchBuffer buffer = batches.remove(batchId);
		if (buffer == null) {
			throw new BatchNotFoundException(batchId);
		}

		// Get an immutable snapshot of the best record per id from this batch and mark buffer finalized.
		Map<String, PriceRecord> delta = buffer.snapshotAndFinalize();
		if (delta.isEmpty()) {
			return; // nothing to commit
		}

		// CAS loop: apply delta on top of the current live map atomically.
		while (true) {
			Map<String, PriceRecord> prev = liveRef.get();
			// Create a mutable copy of prev to apply delta
			Map<String, PriceRecord> next = new HashMap<>(prev);

			for (Map.Entry<String, PriceRecord> e : delta.entrySet()) {
				String id = e.getKey();
				PriceRecord incoming = e.getValue();
				PriceRecord existing = next.get(id);

				// If incoming has later asOf, replace. If equal, use deterministic tie-break.
				if (existing == null || incoming.getAsOf().isAfter(existing.getAsOf())) {
					next.put(id, incoming);
				} else if (incoming.getAsOf().equals(existing.getAsOf())) {
					// Deterministic tie-breaker: choose payload.toString() lexicographically greater
					if (incoming.getPayload().toString().compareTo(existing.getPayload().toString()) > 0) {
						next.put(id, incoming);
					}
				}
			}

			Map<String, PriceRecord> unmodifiable = Collections.unmodifiableMap(next);
			// Attempt atomic swap; if fails, another commit happened concurrently — retry with new prev
			if (liveRef.compareAndSet(prev, unmodifiable)) {
				break;
			}
			// else retry
		}
	}

	@Override
	public void cancelBatch(String batchId) {
		validateBatchId(batchId);

		// Remove batch from in-progress. 
		BatchBuffer removed = batches.remove(batchId);
		if (removed == null) {
			return;
		}
		// Mark buffer finalized to block any concurrent uploads 
		removed.snapshotAndFinalize();
	}

	@Override
	public Optional<PriceRecord> getLastPrice(String id) {
		if (id == null) 
			return Optional.empty();
		return Optional.ofNullable(liveRef.get().get(id));
	}

	
	/*--------------------------------
	 *  Validation helpers
	 *  ------------------------------
	 */

	private void validateBatchId(String batchId) {
		if (batchId == null || batchId.isBlank()) {
			throw new ValidationException("batchId is required");
		}
	}

	private void validateRecord(PriceRecord r) {
		if (r == null) throw new ValidationException("record is required");
		if (r.getId() == null || r.getId().isBlank()) {
			throw new ValidationException("record id is required");
		}
		if (r.getAsOf() == null) {
			throw new ValidationException("record asOf is required");
		}
		if (r.getPayload() == null) {
			throw new ValidationException("record payload is required");
		}
	}
}
