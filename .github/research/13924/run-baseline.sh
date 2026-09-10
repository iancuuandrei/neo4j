#!/usr/bin/env bash
set -uo pipefail

ROOT="$(pwd)"
ART="$ROOT/artifacts/13924"
QUERY_DIR="$ART/queries"
RESULT_DIR="$ART/results"
JFR_DIR="$ART/jfr"
mkdir -p "$QUERY_DIR" "$RESULT_DIR" "$JFR_DIR"

exec > >(tee "$ART/harness.log") 2>&1

echo "=== environment ==="
date -u --iso-8601=seconds
uname -a
java -version
mvn -version || true
git rev-parse HEAD
git status --short

if ! command -v strace >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y strace
fi

VERSION="2026.08.0"
TARBALL="$ROOT/neo4j-community-$VERSION-unix.tar.gz"
DOWNLOAD_URL="https://dist.neo4j.org/neo4j-community-$VERSION-unix.tar.gz"

echo "=== download Neo4j $VERSION ==="
curl --fail --location --retry 5 --retry-delay 2 "$DOWNLOAD_URL" -o "$TARBALL"
sha256sum "$TARBALL" | tee "$ART/neo4j-tarball.sha256"
tar -xzf "$TARBALL" -C "$ROOT"
NEO4J_HOME="$ROOT/neo4j-community-$VERSION"
IMPORT_DIR="$NEO4J_HOME/import"
mkdir -p "$IMPORT_DIR"

cat >> "$NEO4J_HOME/conf/neo4j.conf" <<EOF

dbms.security.auth_enabled=false
server.default_listen_address=127.0.0.1
server.memory.heap.initial_size=512m
server.memory.heap.max_size=3072m
server.memory.pagecache.size=256m
db.transaction.timeout=360s
server.jvm.additional=-Xlog:gc*:file=$ART/gc.log:time,uptime,level,tags
EOF

printf 'alpha,beta\none,two\nthree,four\n' > "$IMPORT_DIR/three.csv"

CS=("$NEO4J_HOME/bin/cypher-shell" -a bolt://127.0.0.1:7687 -u neo4j -p neo4j --format verbose)

run_cypher_text() {
  local text="$1"
  printf '%s\n' "$text" | "${CS[@]}"
}

cleanup() {
  set +e
  if [[ -n "${STRACE_PID:-}" ]]; then
    sudo kill -INT "$STRACE_PID" >/dev/null 2>&1 || true
    wait "$STRACE_PID" >/dev/null 2>&1 || true
  fi
  "$NEO4J_HOME/bin/neo4j" stop >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "=== start server ==="
"$NEO4J_HOME/bin/neo4j" start
for _ in $(seq 1 120); do
  if run_cypher_text 'RETURN 1 AS ready;' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
if ! run_cypher_text 'RETURN 1 AS ready;' >/dev/null 2>&1; then
  echo "Neo4j failed to start"
  cat "$NEO4J_HOME/logs/neo4j.log" || true
  cat "$NEO4J_HOME/logs/debug.log" || true
  exit 1
fi

PID_FILE="$NEO4J_HOME/run/neo4j.pid"
if [[ -f "$PID_FILE" ]]; then
  NEO4J_PID="$(cat "$PID_FILE")"
else
  NEO4J_PID="$(pgrep -f 'org.neo4j.server.CommunityEntryPoint' | head -1)"
fi
echo "neo4j_pid=$NEO4J_PID" | tee "$ART/server.txt"
ps -fp "$NEO4J_PID" | tee -a "$ART/server.txt"

sudo strace -ff -tt -e trace=open,openat,close -p "$NEO4J_PID" -o "$ART/strace" &
STRACE_PID=$!
sleep 2

cpu_ticks() {
  awk '{print $14+$15}' "/proc/$NEO4J_PID/stat"
}

rss_kib() {
  awk '/VmRSS:/ {print $2}' "/proc/$NEO4J_PID/status"
}

hwm_kib() {
  awk '/VmHWM:/ {print $2}' "/proc/$NEO4J_PID/status"
}

reset_graph() {
  run_cypher_text 'MATCH (n) DETACH DELETE n;' >/dev/null
}

setup_original_graph() {
  local n="$1"
  reset_graph
  run_cypher_text "UNWIND range(0, $((n - 1))) AS i CREATE (:l2:l10:l4:l3:l9:l11:l1:l7:l8 {id:i});" >/dev/null
}

setup_two_labels() {
  local p="$1"
  local q="$2"
  reset_graph
  if (( p > 0 )); then
    run_cypher_text "UNWIND range(1, $p) AS i CREATE (:A {i:i});" >/dev/null
  fi
  if (( q > 0 )); then
    run_cypher_text "UNWIND range(1, $q) AS i CREATE (:B {i:i});" >/dev/null
  fi
}

make_csv() {
  local filename="$1"
  local rows="$2"
  : > "$IMPORT_DIR/$filename"
  for i in $(seq 1 "$rows"); do
    printf 'r%s,c%s\n' "$i" "$i" >> "$IMPORT_DIR/$filename"
  done
}

write_original() {
  local runtime="$1"
  local name="$2"
  local mode="$3"
  local inner_kind="$4"
  local outer_file="outer_${name}.csv"
  local inner_file="inner_${name}.csv"
  cp "$IMPORT_DIR/three.csv" "$IMPORT_DIR/$outer_file"
  cp "$IMPORT_DIR/three.csv" "$IMPORT_DIR/$inner_file"

  local inner_clause
  if [[ "$inner_kind" == "csv" ]]; then
    inner_clause="LOAD CSV FROM 'file:///$inner_file' AS alias6"
  else
    inner_clause="UNWIND [['alpha','beta'], ['one','two'], ['three','four']] AS alias6"
  fi

  cat > "$QUERY_DIR/$name.cypher" <<EOF
CYPHER runtime=$runtime $mode
UNWIND ['D','8AFd','DpUDbbF1R'] AS alias0
FOREACH (elem4565 IN range(3, 9) | CREATE (:l2 {k10: elem4565}))
FOR alias1 IN [-1094018090,-1119469534]
FOREACH (elem5121 IN ['A', 'B', 'C'] | CREATE (:l11 {k10: elem5121}))
LOAD CSV FROM 'file:///$outer_file' AS alias2 FIELDTERMINATOR ','
FILTER (size(coll.insert(CASE WHEN range(11, 3, -3) IS :: LIST<ANY> THEN range(11, 3, -3) ELSE [] END, 0, 0)) >= 0)
FOREACH (elem4288 IN ['A', 'B', 'C'] | CREATE (:l8 {k5: elem4288}))
OPTIONAL MATCH (), (:l2&l10&l4&l3&l9&l11&l1&l7&l8)
WHERE (alias0 IS :: STRING | INTEGER)
  AND COUNT {
    LET alias3 = coll.min(CASE WHEN range(1, 10, 2) IS :: LIST<ANY> THEN range(1, 10, 2) ELSE [] END),
        alias4 = coll.min(CASE WHEN alias2 IS :: LIST<ANY> THEN alias2 ELSE [] END),
        alias5 = localdatetime('2024-01-01T00:00:00')
    $inner_clause
  } >= 0
RETURN percentileDisc(toFloatOrNull(alias1), 0.5) AS alias7
SKIP 0 LIMIT 0;
EOF
}

write_minimal() {
  local runtime="$1"
  local name="$2"
  local mode="$3"
  local outer_rows="$4"
  local match_kind="$5"
  local shape="$6"
  local inner_kind="$7"
  local correlated="$8"
  local csv_rows="$9"
  local inner_file="inner_${name}.csv"
  make_csv "$inner_file" "$csv_rows"

  local pattern
  if [[ "$shape" == "two" ]]; then
    pattern="(a:A), (b:B)"
  else
    pattern="(a:A)"
  fi

  local inner_source
  if [[ "$inner_kind" == "csv" ]]; then
    inner_source="LOAD CSV FROM 'file:///$inner_file' AS row"
  else
    inner_source="UNWIND range(1, $csv_rows) AS row"
  fi

  local inner_body
  if [[ "$correlated" == "yes" ]]; then
    inner_body="LET dep = k\n    $inner_source\n    FILTER dep >= 0"
  else
    inner_body="$inner_source"
  fi

  cat > "$QUERY_DIR/$name.cypher" <<EOF
CYPHER runtime=$runtime $mode
UNWIND range(1, $outer_rows) AS k
$match_kind $pattern
WHERE COUNT {
    $inner_body
  } >= 0
RETURN count(*) AS rows;
EOF
}

write_hoisted() {
  local runtime="$1"
  local name="$2"
  local mode="$3"
  local outer_rows="$4"
  local csv_rows="$5"
  local inner_file="inner_${name}.csv"
  make_csv "$inner_file" "$csv_rows"
  cat > "$QUERY_DIR/$name.cypher" <<EOF
CYPHER runtime=$runtime $mode
UNWIND range(1, $outer_rows) AS k
WITH k, COUNT {
  LET dep = k
  LOAD CSV FROM 'file:///$inner_file' AS row
  FILTER dep >= 0
} AS c
OPTIONAL MATCH (a:A), (b:B)
WHERE c >= 0
RETURN count(*) AS rows;
EOF
}

run_file() {
  local name="$1"
  local seconds="$2"
  local record_jfr="${3:-no}"
  local q="$QUERY_DIR/$name.cypher"
  local out="$RESULT_DIR/$name.out"
  local err="$RESULT_DIR/$name.err"
  local timefile="$RESULT_DIR/$name.time"
  local metafile="$RESULT_DIR/$name.meta"
  local jfr_name="${name//[^A-Za-z0-9_]/_}"

  echo "=== RUN $name ==="
  echo "query=$q"
  cat "$q"
  local cpu_before cpu_after rss_before rss_after hwm_after start_ns end_ns status
  cpu_before="$(cpu_ticks)"
  rss_before="$(rss_kib)"
  start_ns="$(date +%s%N)"

  if [[ "$record_jfr" == "yes" ]]; then
    jcmd "$NEO4J_PID" JFR.start name="$jfr_name" settings=profile >/tmp/jfr-start.txt 2>&1 || true
    cat /tmp/jfr-start.txt
  fi

  set +e
  /usr/bin/time -f 'client_elapsed_s=%e\nclient_user_s=%U\nclient_system_s=%S\nclient_maxrss_kib=%M' \
    -o "$timefile" timeout --signal=INT "${seconds}s" "${CS[@]}" < "$q" > "$out" 2> "$err"
  status=$?
  set -e

  end_ns="$(date +%s%N)"
  cpu_after="$(cpu_ticks)"
  rss_after="$(rss_kib)"
  hwm_after="$(hwm_kib)"

  if [[ "$record_jfr" == "yes" ]]; then
    jcmd "$NEO4J_PID" JFR.stop name="$jfr_name" filename="$JFR_DIR/$name.jfr" >/tmp/jfr-stop.txt 2>&1 || true
    cat /tmp/jfr-stop.txt
    if [[ -f "$JFR_DIR/$name.jfr" ]]; then
      jfr summary "$JFR_DIR/$name.jfr" > "$JFR_DIR/$name.summary.txt" 2>&1 || true
      jfr print --events jdk.ExecutionSample,jdk.ObjectAllocationSample "$JFR_DIR/$name.jfr" \
        > "$JFR_DIR/$name.samples.txt" 2>&1 || true
    fi
  fi

  {
    echo "status=$status"
    echo "wall_ns=$((end_ns - start_ns))"
    echo "server_cpu_ticks=$((cpu_after - cpu_before))"
    echo "server_rss_before_kib=$rss_before"
    echo "server_rss_after_kib=$rss_after"
    echo "server_hwm_after_kib=$hwm_after"
    cat "$timefile" 2>/dev/null || true
  } | tee "$metafile"
  echo "--- stdout ---"
  cat "$out"
  echo "--- stderr ---"
  cat "$err"
  echo "=== END $name ==="
}

# Exact plans: EXPLAIN does not execute writes.
for runtime in slotted pipelined interpreted; do
  name="exact_${runtime}_explain"
  write_original "$runtime" "$name" "EXPLAIN" "csv"
  run_file "$name" 60 no
 done

# Exact issue executions, each from the original 128-node graph.
for runtime in slotted pipelined interpreted; do
  setup_original_graph 128
  name="exact_${runtime}_csv"
  write_original "$runtime" "$name" "" "csv"
  run_file "$name" 300 yes
 done

# The issue's controlled substitution.
setup_original_graph 128
write_original slotted "exact_slotted_unwind" "" "unwind"
run_file "exact_slotted_unwind" 180 yes

# Scaled PROFILE runs keep the exact syntax but reduce the original labelled population.
for runtime in slotted pipelined; do
  setup_original_graph 16
  name="profile16_${runtime}_csv"
  write_original "$runtime" "$name" "PROFILE" "csv"
  run_file "$name" 180 no
 done

# Minimal differentiating plans and runs: 5 correlated outer rows, 17 x 19 disconnected optional candidates.
setup_two_labels 17 19
for runtime in slotted pipelined interpreted; do
  name="minimal_${runtime}_explain"
  write_minimal "$runtime" "$name" "EXPLAIN" 5 "OPTIONAL MATCH" two csv yes 3
  run_file "$name" 60 no
 done

for runtime in slotted pipelined interpreted; do
  setup_two_labels 17 19
  name="minimal_${runtime}_csv"
  write_minimal "$runtime" "$name" "" 5 "OPTIONAL MATCH" two csv yes 3
  run_file "$name" 120 no
 done

setup_two_labels 17 19
write_minimal slotted "minimal_slotted_unwind" "" 5 "OPTIONAL MATCH" two unwind yes 3
run_file "minimal_slotted_unwind" 120 no

# Diagnostic intervention: evaluate the correlated scalar before optional candidate expansion.
for runtime in slotted pipelined; do
  setup_two_labels 17 19
  name="minimal_${runtime}_hoisted"
  write_hoisted "$runtime" "$name" "" 5 3
  run_file "$name" 120 no
 done

# Feature ablations.
setup_two_labels 17 19
write_minimal slotted "ablate_match_not_optional" "" 5 "MATCH" two csv yes 3
run_file "ablate_match_not_optional" 120 no

setup_two_labels 17 19
write_minimal slotted "ablate_single_component" "" 5 "OPTIONAL MATCH" one csv yes 3
run_file "ablate_single_component" 120 no

setup_two_labels 17 19
write_minimal slotted "ablate_uncorrelated" "" 5 "OPTIONAL MATCH" two csv no 3
run_file "ablate_uncorrelated" 120 no

# Outer-cardinality sweep, fixed 16 x 16 optional candidates.
for a in 1 2 4 8 16; do
  setup_two_labels 16 16
  name="sweep_outer_${a}"
  write_minimal slotted "$name" "" "$a" "OPTIONAL MATCH" two csv yes 3
  run_file "$name" 120 no
 done

# Candidate-cardinality sweep, one outer row and equal disconnected components.
for n in 1 2 4 8 16 32 64; do
  setup_two_labels "$n" "$n"
  name="sweep_candidates_${n}"
  write_minimal slotted "$name" "" 1 "OPTIONAL MATCH" two csv yes 3
  run_file "$name" 180 no
 done

# CSV-size sweep, fixed one outer row and 16 x 16 optional candidates.
for m in 1 2 4 8 16 32; do
  setup_two_labels 16 16
  name="sweep_csvrows_${m}"
  write_minimal slotted "$name" "" 1 "OPTIONAL MATCH" two csv yes "$m"
  run_file "$name" 180 no
 done

# Scaled PROFILE for actual row counts in the minimal form.
for runtime in slotted pipelined; do
  setup_two_labels 17 19
  name="minimal_${runtime}_profile"
  write_minimal "$runtime" "$name" "PROFILE" 5 "OPTIONAL MATCH" two csv yes 3
  run_file "$name" 180 no
 done

# Stop strace before summarising.
sudo kill -INT "$STRACE_PID" >/dev/null 2>&1 || true
wait "$STRACE_PID" >/dev/null 2>&1 || true
unset STRACE_PID

python3 - <<'PY' "$ART"
import collections
import glob
import json
import os
import re
import sys

art = sys.argv[1]
counts = collections.Counter()
opens = collections.Counter()
closes = 0
for path in glob.glob(os.path.join(art, "strace*")):
    try:
        text = open(path, "r", errors="replace").read()
    except IsADirectoryError:
        continue
    for line in text.splitlines():
        if "close(" in line:
            closes += 1
        if "open(" not in line and "openat(" not in line:
            continue
        match = re.search(r'([^/" ]+\.csv)', line)
        if match:
            filename = match.group(1)
            counts[filename] += 1
            opens[filename] += 1

out = {
    "csv_open_counts": dict(sorted(counts.items())),
    "total_traced_close_calls": closes,
}
with open(os.path.join(art, "strace-summary.json"), "w") as f:
    json.dump(out, f, indent=2, sort_keys=True)
print(json.dumps(out, indent=2, sort_keys=True))
PY

# Compact experiment table.
python3 - <<'PY' "$ART"
import glob
import json
import os
import re
import sys

art = sys.argv[1]
summary_path = os.path.join(art, "strace-summary.json")
summary = json.load(open(summary_path)) if os.path.exists(summary_path) else {"csv_open_counts": {}}
opens = summary.get("csv_open_counts", {})
rows = []
for meta in sorted(glob.glob(os.path.join(art, "results", "*.meta"))):
    name = os.path.basename(meta)[:-5]
    kv = {}
    for line in open(meta, errors="replace"):
        if "=" in line:
            k, v = line.rstrip().split("=", 1)
            kv[k] = v
    inner = opens.get(f"inner_{name}.csv", 0)
    outer = opens.get(f"outer_{name}.csv", 0)
    rows.append({"name": name, "inner_csv_opens": inner, "outer_csv_opens": outer, **kv})
with open(os.path.join(art, "experiment-table.json"), "w") as f:
    json.dump(rows, f, indent=2)
with open(os.path.join(art, "experiment-table.tsv"), "w") as f:
    columns = ["name", "status", "wall_ns", "server_cpu_ticks", "server_rss_before_kib", "server_rss_after_kib", "server_hwm_after_kib", "inner_csv_opens", "outer_csv_opens", "client_elapsed_s", "client_user_s", "client_system_s", "client_maxrss_kib"]
    f.write("\t".join(columns) + "\n")
    for row in rows:
        f.write("\t".join(str(row.get(c, "")) for c in columns) + "\n")
print(open(os.path.join(art, "experiment-table.tsv")).read())
PY

"$NEO4J_HOME/bin/neo4j" stop || true
trap - EXIT

echo "=== complete ==="
date -u --iso-8601=seconds
