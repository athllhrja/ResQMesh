#!/usr/bin/env python3
"""
Skrip Analisis Log Eksperimen KTI ResQMesh.

Membaca satu atau beberapa berkas CSV hasil ekspor dari aplikasi ResQMesh,
menggabungkan log dari beberapa HP, dan menghitung metrik KTI utama:
1. PDR (Packet Delivery Ratio) %
2. RTT (Round-Trip Time) di Origin (Mean, Min, Max, Median) dalam ms
3. Hop Count (Rata-rata dan distribusi jarak hop)
4. Baterai Delta (Selisih % baterai awal - akhir per node)
5. Total Pancaran & Overhead Relay (TX / RELAY events)
"""

import sys
import csv
import glob
import os
import statistics
from collections import defaultdict


def parse_csv_files(file_paths):
    records = []
    for path in file_paths:
        if not os.path.exists(path):
            print(f"[WARN] Berkas tidak ditemukan: {path}")
            continue
        with open(path, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                try:
                    records.append({
                        'runId': row['runId'].strip(),
                        'scenario': row['scenario'].strip(),
                        'nodeId': row['nodeId'].strip(),
                        'event': row['event'].strip(),
                        'messageKey': int(row['messageKey']),
                        'fragIndex': int(row['fragIndex']),
                        'hop': int(row['hop']),
                        'ttl': int(row['ttl']),
                        'rssi': int(row['rssi']),
                        'batteryPct': int(row['batteryPct']),
                        'wallClockMs': int(row['wallClockMs']),
                        'elapsedRealtimeMs': int(row['elapsedRealtimeMs']),
                    })
                except (KeyError, ValueError) as e:
                    continue
    return records


def analyze_experiment(records):
    if not records:
        print("[ERROR] Tidak ada data log yang valid untuk dianalisis.")
        return

    # Kelompokkan data per runId
    runs = defaultdict(list)
    for r in records:
        runs[r['runId']].append(r)

    print("\n" + "=" * 70)
    print("      HASIL ANALISIS EKSPERIMEN KTI - RESQMESH OFFLINE BLE MESH")
    print("=" * 70)

    for run_id, run_records in runs.items():
        scenario = run_records[0]['scenario'] if run_records else "Unknown"
        print(f"\n>>> RUN ID: {run_id} | Skenario: {scenario}")
        print("-" * 70)

        # 1. PDR & Messages Tracking
        sends = {}  # msgKey -> record
        deliveries = {}  # msgKey -> record
        rtt_list = []
        hops_list = []

        # Tracking baterai per node: nodeId -> list of (timestamp, batteryPct)
        battery_by_node = defaultdict(list)

        tx_count = 0
        relay_count = 0

        for r in run_records:
            node = r['nodeId']
            msg_key = r['messageKey']
            event = r['event']
            battery = r['batteryPct']
            time_ms = r['elapsedRealtimeMs']

            if battery >= 0:
                battery_by_node[node].append((time_ms, battery))

            if event == 'SEND':
                sends[msg_key] = r
            elif event in ('DELIVERED', 'ACK_RX'):
                if msg_key not in deliveries:
                    deliveries[msg_key] = r
                if msg_key in sends:
                    # RTT dihitung pada Origin dari SEND -> ACK_RX / DELIVERED pada jam tunggal
                    send_time = sends[msg_key]['elapsedRealtimeMs']
                    rtt = r['elapsedRealtimeMs'] - send_time
                    if rtt >= 0:
                        rtt_list.append(rtt)
            elif event == 'RX':
                if r['hop'] > 0:
                    hops_list.append(r['hop'])
            elif event == 'TX':
                tx_count += 1
            elif event == 'RELAY':
                relay_count += 1

        total_sent = len(sends)
        total_delivered = len(deliveries)
        pdr = (total_delivered / total_sent * 100.0) if total_sent > 0 else 0.0

        print(f"  [1] Packet Delivery Ratio (PDR) : {pdr:.2f}% ({total_delivered}/{total_sent} SOS delivered)")

        # 2. RTT Statistics
        if rtt_list:
            mean_rtt = statistics.mean(rtt_list)
            median_rtt = statistics.median(rtt_list)
            min_rtt = min(rtt_list)
            max_rtt = max(rtt_list)
            print(f"  [2] RTT (Origin Round-Trip Time):")
            print(f"      - Rata-rata (Mean)          : {mean_rtt:.2f} ms")
            print(f"      - Median                    : {median_rtt:.2f} ms")
            print(f"      - Min / Max                 : {min_rtt} ms / {max_rtt} ms")
        else:
            print("  [2] RTT                            : No ACK RTT recorded")

        # 3. Hop Count
        if hops_list:
            avg_hop = statistics.mean(hops_list)
            max_hop = max(hops_list)
            print(f"  [3] Hop Count                      : Rata-rata {avg_hop:.2f} hop (Max: {max_hop} hop)")
        else:
            print("  [3] Hop Count                      : 0 hop (Direct)")

        # 4. Battery Delta
        print("  [4] Konsumsi Baterai (Delta % per Node):")
        for node, samples in battery_by_node.items():
            if len(samples) >= 2:
                samples.sort(key=lambda x: x[0])
                first_bat = samples[0][1]
                last_bat = samples[-1][1]
                delta = first_bat - last_bat
                print(f"      - Node {node} : {first_bat}% -> {last_bat}% (Delta: {delta}%)")
            elif samples:
                print(f"      - Node {node} : {samples[0][1]}% (Konstan)")

        # 5. Overhead
        print(f"  [5] Aktivasi Pancaran BLE          : Total TX={tx_count}, Total RELAY={relay_count}")

    print("\n" + "=" * 70)


def main():
    if len(sys.argv) < 2:
        print("Penggunaan: python process_experiment.py <file1.csv> <file2.csv> ... ATAU <direktori_csv>")
        sys.exit(1)

    inputs = sys.argv[1:]
    files = []
    for item in inputs:
        if os.path.isdir(item):
            files.extend(glob.glob(os.path.join(item, "*.csv")))
        else:
            files.append(item)

    records = parse_csv_files(files)
    analyze_experiment(records)


if __name__ == '__main__':
    main()
