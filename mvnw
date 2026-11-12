#!/usr/bin/env sh
set -eu
MAVEN_VERSION=3.10.0
WRAPPER_HOME="${HOME}/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"
MAVEN_EXE="${WRAPPER_HOME}/bin/mvn"
if [ ! -x "$MAVEN_EXE" ]; then
  echo "Downloading Apache Maven ${MAVEN_VERSION}..."
  mkdir -p "$WRAPPER_HOME"
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  if command -v curl >/dev/null 2>&1; then
    curl -fL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -o "$TMP_DIR/maven.tar.gz"
  else
    wget -O "$TMP_DIR/maven.tar.gz" "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  fi
  tar -xzf "$TMP_DIR/maven.tar.gz" -C "$TMP_DIR"
  cp -R "$TMP_DIR/apache-maven-${MAVEN_VERSION}/." "$WRAPPER_HOME/"
fi
exec "$MAVEN_EXE" "$@"
