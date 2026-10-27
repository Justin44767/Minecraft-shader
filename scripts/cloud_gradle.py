#!/usr/bin/env python3
"""Run the checked-in wrapper with the prepared cloud JDK and Gradle cache."""
import os
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
environment = os.environ.copy()
environment['JAVA_HOME'] = '/workspace/.tools/jdk-25.0.5.0.3'
environment['GRADLE_USER_HOME'] = '/workspace/.gradle'
raise SystemExit(subprocess.call([str(root / 'gradlew'), *sys.argv[1:]], cwd=root, env=environment))
