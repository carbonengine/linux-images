#!/usr/bin/env bash
# Builds an image and pushes it to a registry under every tag from compute-tags.sh.
#
# usage: build-and-push.sh <image-name> <full-ref> <is-default> <sha> <registry> <context-dir> [dockerfile]
#   context-dir   build context, relative to the repository root
#   dockerfile    file name inside context-dir (default: Dockerfile)
# The agent must already be logged in to the registry (TeamCity Docker Support build feature).
set -euo pipefail

image="$1"; ref="$2"; is_default="$3"; sha="$4"; registry="$5"
context_dir="$6"; dockerfile="${7:-Dockerfile}"
engine=docker

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
context="$here/../../$context_dir"
file="$context/$dockerfile"
[[ -f "$file" ]] || { echo "Dockerfile not found: $file" >&2; exit 1; }

# pull requests only verify that the image builds; nothing is pushed
if [[ "$ref" == refs/pull/* ]]; then
    pr="${ref#refs/pull/}"; pr="${pr%%/*}"
    "$engine" build -f "$file" -t "$image:pr-$pr" "$context"
    exit 0
fi

# the checkout only contains the triggering ref, so ask the remote for all existing tags
existing_tags="$(git ls-remote --tags --refs origin | sed -E 's#.*refs/tags/##')"

mapfile -t tags < <("$here/compute-tags.sh" "$ref" "$is_default" "$sha" <<<"$existing_tags")
echo "Tags: ${tags[*]}"
echo "##teamcity[buildNumber '${tags[0]}']"

args=()
for tag in "${tags[@]}"; do
    args+=(-t "$registry/$image:$tag")
done

"$engine" build -f "$file" "${args[@]}" "$context"

for tag in "${tags[@]}"; do
    "$engine" push "$registry/$image:$tag"
done
