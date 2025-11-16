package com.finance.instrument_price_store.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class PriceRecord {
	private final String id;
	private final Instant asOf;
	private final Map<String, Object> payload;


	public PriceRecord(String id, Instant asOf, Map<String, Object> payload) {
		this.id = Objects.requireNonNull(id, "id");
		this.asOf = Objects.requireNonNull(asOf, "asOf");
		this.payload = Objects.requireNonNull(payload, "payload");
	}


	public String getId() {
		return id; 
	}
	public Instant getAsOf() { 
		return asOf; 
	}
	public Map<String, Object> getPayload() {
		return payload; 
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof PriceRecord)) return false;
		PriceRecord that = (PriceRecord) o;
		return id.equals(that.id) && asOf.equals(that.asOf) && payload.equals(that.payload);
	}


	@Override
	public int hashCode() {
		return Objects.hash(id, asOf, payload);
	}


	@Override
	public String toString() {
		return "PriceRecord{" +
				"id='" + id + '\'' +
				", asOf=" + asOf +
				", payload=" + payload +
				'}';
	}
}