#!/usr/bin/env bash

current_release=$(git describe --tags --abbrev=0 --match 'v*')
old_release=$(git describe --tags --abbrev=0 --match 'v*' $current_release^)
echo "The previous release tag is $old_release"

changes=$(git log --oneline --no-decorate $old_release..)
echo "$changes"

mkdir -p whatsnew && cd whatsnew
echo "$changes" > whatsnew-en-US
for lang in de-CH fr-CH it-CH; do
	ln -fs whatsnew-en-US whatsnew-$lang
done
