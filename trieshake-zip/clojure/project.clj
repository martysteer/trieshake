(defproject trieshake-zip "0.1.0"
  :description "Apply trieshake algorithm to zip archives"
  :url "https://github.com/martysteer/trieshake"
  :license {:name "CC-BY-SA 4.0"
            :url "https://creativecommons.org/licenses/by-sa/4.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.0.219"]]
  :main trieshake-zip.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
