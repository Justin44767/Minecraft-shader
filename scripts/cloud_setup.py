#!/usr/bin/env python3
"""Reproduce this Linux cloud toolchain without changing system trust or repository dependencies."""
import hashlib
import os
from pathlib import Path
import platform
import subprocess
import tarfile
import urllib.parse
import urllib.request

TOOLS = Path('/workspace/.tools')
JDK = TOOLS / 'jdk-25.0.5.0.3'
ARCHIVE = TOOLS / 'downloads/jdk25.tar.gz'
URL = 'https://download.oracle.com/java/25/archive/jdk-25.0.5_linux-x64_bin.tar.gz'
SHA256 = '6deafad80454d1f968105b1f582d89b8c2e88a5dfc98116a8226e0ec2fd5df8e'

def prepare():
    if platform.system() != 'Linux' or platform.machine() not in ('x86_64', 'amd64'):
        raise SystemExit('Cloud script requires Linux x86_64. For other systems, install a Java 25 JDK and use gradlew.bat/gradlew directly.')
    if not (JDK / 'bin/javac').is_file():
        ARCHIVE.parent.mkdir(parents=True, exist_ok=True)
        if not ARCHIVE.is_file():
            with urllib.request.urlopen(URL, timeout=90) as source, ARCHIVE.open('wb') as target:
                while chunk := source.read(1024 * 1024):
                    target.write(chunk)
        with ARCHIVE.open('rb') as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != SHA256:
                raise SystemExit('JDK SHA-256 mismatch; refusing to extract. Remove the failed download and investigate.')
        with tarfile.open(ARCHIVE) as archive:
            archive.extractall(TOOLS, filter='data')
    subprocess.run([str(JDK / 'bin/java'), '-version'], check=True)
    gradle_home = Path('/workspace/.gradle')
    gradle_home.mkdir(exist_ok=True)
    properties = gradle_home / 'gradle.properties'
    existing = properties.read_text() if properties.exists() else ''
    proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
    if proxy.hostname:
        if proxy.username or proxy.password:
            raise SystemExit('Authenticated proxy detected. Use platform-supported Java proxy configuration; no credentials are written.')
        additions = {
            'systemProp.https.proxyHost': proxy.hostname,
            'systemProp.https.proxyPort': str(proxy.port or 80),
            'systemProp.http.proxyHost': proxy.hostname,
            'systemProp.http.proxyPort': str(proxy.port or 80),
            'systemProp.javax.net.ssl.trustStore': '/etc/ssl/certs/java/cacerts',
        }
        # Preserve all unrelated Gradle settings. Do not emit proxy values to logs.
        lines = [line for line in existing.splitlines() if line.split('=', 1)[0] not in additions]
        lines.extend(key + '=' + value for key, value in additions.items())
        properties.write_text('\n'.join(lines) + '\n')
    print('Cloud Java 25 and Gradle proxy/trust configuration prepared.')

if __name__ == '__main__':
    prepare()
