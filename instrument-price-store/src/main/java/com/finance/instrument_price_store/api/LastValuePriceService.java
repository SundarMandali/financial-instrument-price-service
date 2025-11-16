package com.finance.instrument_price_store.api;

import java.util.List;
import java.util.Optional;

import com.finance.instrument_price_store.model.PriceRecord;


public interface LastValuePriceService {
	void startBatch(String batchId);
	void uploadChunk(String batchId, List<PriceRecord> chunk);
	void completeBatch(String batchId);
	void cancelBatch(String batchId);
	Optional<PriceRecord> getLastPrice(String id);
}