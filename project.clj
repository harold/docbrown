(defproject docbrown "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [juxt/crux "19.04-1.0.2-alpha"]
                 [org.rocksdb/rocksdbjni "5.17.2"]
                 [commons-codec/commons-codec "1.12"]]
  :main ^:skip-aot docbrown.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
