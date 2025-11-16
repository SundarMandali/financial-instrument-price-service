package com.finance.instrument_price_store.exception;

public class BatchNotFoundException extends RuntimeException {
	public BatchNotFoundException(String batchId) {
		super("Batch not found: " + batchId);
	}
}