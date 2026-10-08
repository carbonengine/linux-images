#!/usr/bin/env bash
# Prints the image tags (one per line) for a build.
#
# usage: compute-tags.sh <full-ref> <is-default-branch: true|false> <commit-sha> < existing-tag-names
#
# Existing git tag names (one per line, e.g. v1.2.3) are read from stdin and are used to decide
# whether a release is the highest one for its minor / major.
#
#   refs/tags/v2.2.0       -> 2.2.0-<sha>, 2.2.0, and 2.2 / 2 if it is the highest stable 2.2.x / 2.x.x
#   refs/tags/v2.2.0-rc1   -> 2.2.0-rc1-<sha>, 2.2.0-rc1 (prereleases never roll 2.2 or 2)
#   refs/pull/12/head      -> pr-12-<sha>
#   default branch (main)  -> main-<sha> and latest
#   any other branch       -> <branch>-<sha>
set -euo pipefail

ref="$1"
is_default="$2"
sha="$(printf '%s' "$3" | cut -c1-8)"

# Docker tags allow [A-Za-z0-9_.-], max 128 chars, and may not start with '.' or '-'
sanitize() {
    local s
    s="$(printf '%s' "$1" | sed -E 's#[^A-Za-z0-9_.-]+#-#g; s#^[.-]+##')"
    printf '%s' "${s:0:100}"
}

semver_re='^v?([0-9]+)\.([0-9]+)\.([0-9]+)(-[0-9A-Za-z.-]+)?$'

# true if $1 is the highest of itself and all stable versions in stdin matching grep pattern $2
is_highest() {
    local current="$1" pattern="$2" top
    top="$( { cat; echo "$current"; } | grep -E "$pattern" | sort -V | tail -n1)"
    [[ "$top" == "$current" ]]
}

case "$ref" in
    refs/tags/*)
        name="${ref#refs/tags/}"
        if [[ "$name" =~ $semver_re ]]; then
            major="${BASH_REMATCH[1]}"; minor="${BASH_REMATCH[2]}"; prerelease="${BASH_REMATCH[4]}"
            version="${name#v}"
            echo "$version-$sha"
            echo "$version"
            if [[ -z "$prerelease" ]]; then
                # stable versions only: x.y.z with no prerelease suffix, leading v removed
                stable="$(sed -E 's/^v//' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' || true)"
                if is_highest "$version" "^$major\.$minor\.[0-9]+$" <<<"$stable"; then
                    echo "$major.$minor"
                fi
                if is_highest "$version" "^$major\.[0-9]+\.[0-9]+$" <<<"$stable"; then
                    echo "$major"
                fi
            fi
        else
            echo "$(sanitize "$name")-$sha"
        fi
        ;;
    refs/pull/*)
        pr="${ref#refs/pull/}"
        echo "pr-${pr%%/*}-$sha"
        ;;
    *)
        branch="${ref#refs/heads/}"
        echo "$(sanitize "$branch")-$sha"
        if [[ "$is_default" == "true" ]]; then
            echo "latest"
        fi
        ;;
esac
