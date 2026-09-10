#!/usr/bin/env bash
set -euo pipefail

SOURCE=".github/research/13924/run-baseline.sh"
TARGET="$(mktemp /tmp/neo4j-13924-XXXXXX.sh)"
cp "$SOURCE" "$TARGET"

python3 - "$TARGET" <<'PYFIX'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
text = text.replace("set -uo pipefail", "set -euo pipefail", 1)
text = text.replace(
    'inner_body="LET dep = k\\n    $inner_source\\n    FILTER dep >= 0"',
    'inner_body="LET dep = k"$\'\\n\'"    $inner_source"$\'\\n\'"    FILTER dep >= 0"',
    1,
)
text = text.replace("python3 - <<'PY' \"$ART\"", "python3 - \"$ART\" <<'PY'", 2)
path.write_text(text)
PYFIX

chmod +x "$TARGET"
exec "$TARGET"
