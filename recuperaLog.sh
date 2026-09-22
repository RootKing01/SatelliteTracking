#!/bin/bash

# =============================================================================
# satellite_logs.sh
# Raccoglie i log del container satellitetracker-app in full_logs.txt
# e suggerisce i comandi per filtrare i log per servizio/evento.
#
# USO:
#   chmod +x satellite_logs.sh
#   ./satellite_logs.sh
#
# GREP / SED UTILI DOPO L'ESECUZIONE:
# -----------------------------------------------------------------------------
#
# ── SEZIONI PRINCIPALI (inizio/fine blocchi) ─────────────────────────────────
#   grep -n "AGGIORNAMENTO TLE\|INIZIO DOWNLOAD\|COMPLETATO\|INIZIATO" full_logs.txt
#
# ── TROVARE LE RIGHE DI INIZIO DI OGNI FETCH ─────────────────────────────────
#   grep -n "DELTA FETCH\|DOWNLOAD COMPLETO\|fetchAndSaveStations\|CELESTRAK\|CelesTrak" full_logs.txt
#
# ── ESTRARRE UN INTERVALLO TRA DUE RIGHE (es. riga 100 → 300) ────────────────
#   sed -n '100,300p' full_logs.txt
#
# ── LOG SOLO DI SpaceTrackService ─────────────────────────────────────────────
#   grep "SpaceTrackService" full_logs.txt
#   grep -n "SpaceTrackService" full_logs.txt | head -50
#
# ── LOG SOLO DI TleDataService ────────────────────────────────────────────────
#   grep "TleDataService" full_logs.txt
#
# ── LOG SOLO DI CelestrakService ──────────────────────────────────────────────
#   grep "CelestrakService" full_logs.txt
#
# ── LOG SOLO DI TelegramNotificationService ───────────────────────────────────
#   grep "TelegramNotificationService" full_logs.txt
#
# ── LOG SOLO DI SatellitePassService ─────────────────────────────────────────
#   grep "SatellitePassService" full_logs.txt
#
# ── SOLO ERRORI E WARNING (tutti i servizi) ───────────────────────────────────
#   grep " ERROR \| WARN " full_logs.txt
#   grep "❌\|⚠️" full_logs.txt
#
# ── SOLO SUCCESSI ─────────────────────────────────────────────────────────────
#   grep "✅" full_logs.txt
#
# ── RISULTATI PARSING (salvati/saltati/errori) ────────────────────────────────
#   grep "Salvati\|Saltati\|Errori\|Nuovi satelliti\|PARSING JSON" full_logs.txt
#
# ── LOGIN / SESSIONE SPACE-TRACK ──────────────────────────────────────────────
#   grep "login\|LOGIN\|Cookie\|sessione\|SUCCESSO\|definitivamente fallito" full_logs.txt
#
# ── RATE LIMIT E COOLDOWN ─────────────────────────────────────────────────────
#   grep "rate limit\|cooldown\|429\|Concurrency" full_logs.txt
#
# ── TIMEOUT ───────────────────────────────────────────────────────────────────
#   grep -i "timeout\|ReadTimeout\|WriteTimeout" full_logs.txt
#
# ── DELTA FETCH: solo il ciclo di un singolo fetch (SpaceTrack) ───────────────
#   # 1. Trova la riga di inizio:
#   grep -n "DELTA FETCH" full_logs.txt
#   # 2. Estrai dall'inizio al completamento (es. riga 249 → 280):
#   sed -n '249,280p' full_logs.txt
#
# ── TUTTO PRIMA DEL FETCH CELESTRAK ──────────────────────────────────────────
#   # 1. Trova la riga dove parte Celestrak:
#   grep -n "INIZIO DOWNLOAD DA CELESTRAK" full_logs.txt | head -5
#   # 2. Prendi tutto quello che viene prima (es. riga 270):
#   head -n 269 full_logs.txt
#
# ── CONFRONTO TEMPORALE (quant'è durato un fetch) ────────────────────────────
#   grep "DELTA FETCH\|COMPLETATO\|fallito definitivamente" full_logs.txt | head -20
#
# =============================================================================

CONTAINER_NAME="satellite-app"
OUTPUT_FILE="full_logs.txt"

echo "🛰️  Satellite Tracker - Raccolta log"
echo "======================================"

# Verifica che il container esista e sia in esecuzione
if ! sudo docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "❌ Container '${CONTAINER_NAME}' non trovato o non in esecuzione."
    echo "   Containers attivi:"
    docker ps --format '   - {{.Names}}'
    exit 1
fi

# Pulisce il file precedente se esiste
if [ -f "$OUTPUT_FILE" ]; then
    echo "🗑️  Pulizia file precedente: $OUTPUT_FILE"
    > "$OUTPUT_FILE"
fi

# Scarica i log del container
echo "📥 Download log da '${CONTAINER_NAME}'..."
sudo docker logs "$CONTAINER_NAME" > "$OUTPUT_FILE" 2>&1

TOTAL_LINES=$(wc -l < "$OUTPUT_FILE")
FILE_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)

echo ""
echo "✅ Log salvati in: $OUTPUT_FILE"
echo "   Righe totali:  $TOTAL_LINES"
echo "   Dimensione:    $FILE_SIZE"
echo ""
echo "── Riepilogo rapido ──────────────────────────────────────"
echo "  Errori:    $(grep -c ' ERROR ' "$OUTPUT_FILE") righe"
echo "  Warning:   $(grep -c ' WARN '  "$OUTPUT_FILE") righe"
echo "  SpaceTrack fetch: $(grep -c 'DELTA FETCH' "$OUTPUT_FILE") fetch"
echo "  CelesTrak fetch:  $(grep -c 'INIZIO DOWNLOAD DA CELESTRAK' "$OUTPUT_FILE") fetch"
echo "  Login ST:  $(grep -c 'login SUCCESSO' "$OUTPUT_FILE") successi"
echo "  Timeout:   $(grep -ci 'timeout' "$OUTPUT_FILE") occorrenze"
echo "──────────────────────────────────────────────────────────"
echo ""
echo "💡 Vedi i commenti in cima a questo script per i grep/sed consigliati."
