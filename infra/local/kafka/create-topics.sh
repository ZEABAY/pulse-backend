#!/bin/bash
set -e

# KRaft: Internal listener PLAINTEXT kafka:29092 (ADVERTISED_LISTENERS)
# Host'tan erişim: localhost:9092 (PLAINTEXT_HOST)
# Aynı ağdaki container'dan: kafka:29092 kullan
BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:29092}"

echo "[kafka-init] Waiting for Kafka at $BOOTSTRAP..."
until /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "$BOOTSTRAP" 2>/dev/null; do
  echo "[kafka-init] Kafka not ready, retrying in 2s..."
  sleep 2
done

echo "[kafka-init] Kafka ready. Creating topics..."

/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" \
  --create --if-not-exists \
  --topic pulse.auth.user-registered \
  --partitions 1 --replication-factor 1

/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" \
  --create --if-not-exists \
  --topic pulse.auth.email-verification \
  --partitions 1 --replication-factor 1

echo "[kafka-init] Topics created successfully."
