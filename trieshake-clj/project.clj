(defproject trieshake "0.1.0"
  :description "Radix trie-based filesystem reorganizer"
  :dependencies [[org.clojure/clojure "1.11.1"]]
  :main trieshake.core
  :aot [trieshake.core]
  :profiles {:uberjar {:aot :all}})
