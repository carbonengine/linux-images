#!/usr/bin/env bash
# Builds build/<variant>/Containerfile and pushes it to ECR under every tag from compute-tags.sh.
#
# usage: build-and-push.sh <variant> <image-name> <full-ref> <is-default> <sha> <ecr-registry>
# The agent must already be logged in to the registry (TeamCity Docker Support build feature).
set -euo pipefail

variant="$1"; image="$2"; ref="$3"; is_default="$4"; sha="$5"
registry="$6"
engine=docker

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
context="$here/../../build/$variant"

# pull requests only verify that the image builds; nothing is pushed
if [[ "$ref" == refs/pull/* ]]; then
    pr="${ref#refs/pull/}"; pr="${pr%%/*}"
    "$engine" build -t "$image:pr-$pr" "$context"
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

"$engine" build "${args[@]}" "$context"

for tag in "${tags[@]}"; do
    "$engine" push "$registry/$image:$tag"
done
