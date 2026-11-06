::
:: elDoc SASS Styles compile script
:: See more: https://sass-lang.com/documentation/cli/dart-sass
::
sass --no-source-map ^
  --style=compressed ^
  --load-path=sass ^
  "eldoc/eldoc.scss:../src/main/webapp/resources/eldoc/5_0_0/css/eldoc.css" ^
  "eldoc/eldoc-print.scss:../src/main/webapp/resources/eldoc/5_0_0/css/eldoc-print.css" ^
  "eldoc/layout.scss:../src/main/webapp/resources/eldoc/5_0_0/css/layout.css"
