# 📈 Financial Instrument Price Service

A Spring Boot application that manages last known prices of financial instruments using an in-memory, batch-based, thread-safe design.

📌 Overview

This project implements a production-style backend service for storing the latest price of financial instruments (such as stocks, bonds, ETFs, currencies, commodities, etc.).

The key feature of this service is batch-based ingestion—producers upload prices in controlled batches, and consumers request the last valid price for any instrument.

The entire system is:

✔ In-memory (no database)

✔ Thread-safe (supports concurrent producers & consumers)

✔ Batch-driven (atomic visibility of uploaded data)

✔ Well-tested with JUnit 5

✔ Clean code following industry standards

🔍 Problem the Service Solves

Financial systems often need to track only the latest price for each asset.
Example:

What is the latest price of AAPL?

What is the latest price of BTC-USD?

But producers send thousands of instrument prices at a time (called a batch).

This service ensures:

🎯 1. Producers upload in batches

A batch lifecycle consists of:

startBatch → uploadChunk → uploadChunk → ... → complete or cancel

🎯 2. Consumers always get stable data

They must never see “half uploaded” batches.

All the chunk data becomes visible only after batch completion.

🎯 3. Cancelled batches are discarded

No partial or incorrect data is exposed.

🏗️ Core Features

✔ Start a new batch

Initializes an empty temporary store.

✔ Upload 1000-record chunks

Each chunk contains multiple price records.

✔ Complete the batch

Atomically replaces the old store with the new one.

✔ Cancel the batch

Drops all staged (temporary) data.

✔ Get the last price for an instrument

Returns the price with the most recent asOf timestamp.

🧠 Architecture & Concurrency Design

🟦 ConcurrentHashMap

Used for storing records in both:

active price store

temporary batch store

Reason:
Supports thread-safe read/write without locking whole map.

🟦 AtomicReference

Stores the active price map.

Reason:
Allows atomic swap of entire dataset when batch completes.

🟦 Batch Isolation

All incoming chunks go into a temporary map.
Only when completeBatch() is called:

atomicReference.set(tempMap)


Consumers instantly see the new dataset.

Writer & reader traffic never blocks each other.

🧪 Testing

The tests verify:

✔ Starting, uploading, completing batches

✔ Fetching last price

✔ Rejecting invalid operation order

✔ Cancel behavior

✔ Timestamp comparison logic

✔ Thread-safety scenarios

(run with mvn test)

🚀 How to Run
1. Clone the repo

git clone https://github.com/<your-username>/<repo-name>

cd <repo-name>

2. Run the application

mvn spring-boot:run

🛠️ Sample JSON for Batch Upload

Start:

POST /api/batch/start

Upload chunk:

POST /api/batch/upload
[

  {
    "id": "AAPL",
    "asOf": "2025-01-01T10:15:30",
    "payload": { "price": 178.50 }
  },
  {
    "id": "TSLA",
    "asOf": "2025-01-01T10:15:31",
    "payload": { "price": 250.40 }
  }
]

Complete:

POST /api/batch/complete

Get last price:

GET /api/price/AAPL

This project demonstrates:

✔ Clean OOP design

✔ Concurrency handling

✔ Batch processing

✔ Atomic data visibility

✔ Custom exception design

✔ Good testing practices

✔ REST API design

⭐ Future Enhancements

Add persistence (Redis / PostgreSQL / Kafka consumer)

Add metrics (Prometheus + Micrometer)

Add versioned price history

Add authentication / JWT

Add async batch processing
