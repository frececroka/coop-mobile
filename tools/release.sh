#!/usr/bin/env fish

set n "$argv[1]"

git add -p

git commit -m "Release $n"
git tag "v$n"
git push
git push --tags
