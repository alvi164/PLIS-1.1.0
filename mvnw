#!/bin/sh
set -eu

MAVEN_VERSION="3.9.16"
EXPECTED_SHA512="831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6"
DISTRIBUTION_URL="https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
DISTRIBUTION_ROOT="${HOME}/.m2/wrapper/dists/plis"
MAVEN_HOME="${DISTRIBUTION_ROOT}/apache-maven-${MAVEN_VERSION}"

if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  mkdir -p "${DISTRIBUTION_ROOT}"
  ARCHIVE="$(mktemp "${TMPDIR:-/tmp}/plis-maven.XXXXXX.tar.gz")"
  trap 'rm -f "${ARCHIVE}"' EXIT INT TERM
  printf '%s\n' "Downloading Apache Maven ${MAVEN_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "${DISTRIBUTION_URL}" -o "${ARCHIVE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "${DISTRIBUTION_URL}" -O "${ARCHIVE}"
  else
    printf '%s\n' "curl or wget is required to download Maven." >&2
    exit 1
  fi
  if command -v sha512sum >/dev/null 2>&1; then
    ACTUAL_SHA512="$(sha512sum "${ARCHIVE}" | awk '{print $1}')"
  else
    ACTUAL_SHA512="$(shasum -a 512 "${ARCHIVE}" | awk '{print $1}')"
  fi
  if [ "${ACTUAL_SHA512}" != "${EXPECTED_SHA512}" ]; then
    printf '%s\n' "The downloaded Maven archive failed its SHA-512 integrity check." >&2
    exit 1
  fi
  tar -xzf "${ARCHIVE}" -C "${DISTRIBUTION_ROOT}"
fi

exec "${MAVEN_HOME}/bin/mvn" "$@"
