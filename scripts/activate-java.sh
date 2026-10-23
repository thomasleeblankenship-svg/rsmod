#!/usr/bin/env bash
# Source this file; it does not install dependencies or start services.
export JAVA_HOME="${JAVA_HOME:-/workspace/tooling/jdk-21.0.8+9}"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/tooling/gradle}"
if [[ -d /workspace/tooling/android-sdk ]]; then
    export ANDROID_HOME="${ANDROID_HOME:-/workspace/tooling/android-sdk}"
    export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
    export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/workspace/tooling/android-user}"
fi
# Java does not automatically honor HTTPS_PROXY. Keep TLS verification enabled.
RUNEMOBILE_JAVA_NETWORK_OPTIONS="$(python3 - <<'PY'
import os
from pathlib import Path
from urllib.parse import urlsplit
proxy = urlsplit(os.environ.get('HTTPS_PROXY', ''))
options = []
if proxy.hostname:
    for scheme in ('http', 'https'):
        options.extend([f'-D{scheme}.proxyHost={proxy.hostname}',
                        f'-D{scheme}.proxyPort={proxy.port or 80}'])
trust = Path('/etc/ssl/certs/java/cacerts')
if trust.is_file():
    options.append(f'-Djavax.net.ssl.trustStore={trust}')
print(' '.join(options))
PY
)"
if [[ -n "$RUNEMOBILE_JAVA_NETWORK_OPTIONS" && "${JAVA_TOOL_OPTIONS:-}" != *"$RUNEMOBILE_JAVA_NETWORK_OPTIONS"* ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }$RUNEMOBILE_JAVA_NETWORK_OPTIONS"
fi
unset RUNEMOBILE_JAVA_NETWORK_OPTIONS
