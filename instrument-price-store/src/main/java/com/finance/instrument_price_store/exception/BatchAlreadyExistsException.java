package com.finance.instrument_price_store.exception;

public class BatchAlreadyExistsException extends RuntimeException {
	public BatchAlreadyExistsException(String batchId) {
		super("Batch already exists: " + batchId);
	}
}