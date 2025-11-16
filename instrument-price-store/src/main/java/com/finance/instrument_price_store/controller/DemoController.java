package com.finance.instrument_price_store.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finance.instrument_price_store.api.LastValuePriceService;
import com.finance.instrument_price_store.model.PriceRecord;

/**
 * Simple demo controller to manually exercise the Instrument Price Store via HTTP.
 *
 * Endpoints:
 *  - POST /demo/start/{batchId}      -> start a batch
 *  - POST /demo/upload/{batchId}     -> upload a chunk (JSON array of PriceRecord)
 *  - POST /demo/complete/{batchId}   -> complete a batch (commit)
 *  - POST /demo/cancel/{batchId}     -> cancel a batch
 *  - GET  /demo/price/{id}           -> get last price for id
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

	private final LastValuePriceService service;

	public DemoController(LastValuePriceService service) {
		this.service = service;
	}

	@PostMapping("/start/{batchId}")
	public ResponseEntity<String> start(@PathVariable String batchId) {
		service.startBatch(batchId);
		return ResponseEntity.ok("batch started: " + batchId);
	}

	@PostMapping("/upload/{batchId}")
	public ResponseEntity<String> upload(@PathVariable String batchId, @RequestBody List<PriceRecord> chunk) {
		service.uploadChunk(batchId, chunk);
		return ResponseEntity.ok("chunk uploaded: size=" + (chunk == null ? 0 : chunk.size()));
	}

	@PostMapping("/complete/{batchId}")
	public ResponseEntity<String> complete(@PathVariable String batchId) {
		service.completeBatch(batchId);
		return ResponseEntity.ok("batch completed: " + batchId);
	}

	@PostMapping("/cancel/{batchId}")
	public ResponseEntity<String> cancel(@PathVariable String batchId) {
		service.cancelBatch(batchId);
		return ResponseEntity.ok("batch cancelled: " + batchId);
	}

	@GetMapping("/price/{id}")
	public ResponseEntity<PriceRecord> price(@PathVariable String id) {
		return service.getLastPrice(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}

