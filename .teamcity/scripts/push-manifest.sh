#!/usr/bin/env bash
# Publishes every tag from compute-tags.sh as a multi-arch manifest list that points at the per-architecture
# images "<first-tag>-<arch>" pushed by build-and-push.sh.
#
# usage: push-manifest.sh <image-name> <full-ref> <is-default> <sha> <registry> "<arch> [<arch>...]"
# The agent must already be logged in to the registry (TeamCity Docker Support build feature).
set -euo pipefail

image="$1"; ref="$2"; is_default="$3"; sha="$4"; registry="$5"; read -ra archs <<<"$6"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

existing_tags="$(git ls-remote --tags --refs origin | sed -E 's#.*refs/tags/##')"

mapfile -t tags < <("$here/compute-tags.sh" "$ref" "$is_default" "$sha" <<<"$existing_tags")
echo "Tags: ${tags[*]}"
echo "##teamcity[buildNumber '${tags[0]}']"

sources=()
for arch in "${archs[@]}"; do
    sources+=("$registry/$image:${tags[0]}-$arch")
done

for tag in "${tags[@]}"; do
    docker buildx imagetools create -t "$registry/$image:$tag" "${sources[@]}"
done
