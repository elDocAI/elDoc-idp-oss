#!/usr/bin/env bash
#
# elDoc SASS Styles compile script (watch mode, Linux/macOS)
# See more: https://sass-lang.com/documentation/cli/dart-sass
#

set -euo pipefail

cd "$(dirname "$0")"

sass --no-source-map \
  --style=compressed \
  --watch --poll \
  --load-path=sass \
  "eldoc/eldoc.scss:../src/main/webapp/resources/eldoc/5_0_0/css/eldoc.css" \
  "eldoc/eldoc-print.scss:../src/main/webapp/resources/eldoc/5_0_0/css/eldoc-print.css" \
  "eldoc/layout.scss:../src/main/webapp/resources/eldoc/5_0_0/css/layout.css"
