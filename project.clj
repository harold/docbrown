(defproject docbrown "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [juxt/crux "19.04-1.0.2-alpha"]
                 [org.rocksdb/rocksdbjni "5.17.2"]
                 [ring "1.7.1"]
                 [http-kit "2.3.0"]
                 [bidi "2.1.6"]
                 [metosin/muuntaja "0.6.1"]
                 [reagent "0.8.1"]
                 [cljs-ajax "0.7.5"]]
  :plugins [[lein-figwheel "0.5.17"]
            [lein-cljsbuild "1.1.7"]
            [lein-garden "0.3.0"]
            [lein-eftest "0.5.3"]]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/"]
                        :figwheel true
                        :compiler {:main "docbrown.webapp"
                                   :asset-path "/js/out"
                                   :output-to "resources/public/js/app.js"
                                   :output-dir "resources/public/js/out"
                                   :optimizations :none}}
                       {:id "prod"
                        :source-paths ["src/"]
                        :compiler {:output-to "resources/public/js/app.js"
                                   :optimizations :advanced}}]}
  :figwheel {:css-dirs ["resources/public/css"]}
  :garden {:builds [{:id "site"
                     :source-paths ["src/"]
                     :stylesheet docbrown.css.site/site
                     :compiler {:output-to "resources/public/css/site.css"
                                :pretty-print? false}}]}
  :main ^:skip-aot docbrown.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :clean-targets ^{:protect false} [:target-path
                                    ".nrepl-port"
                                    "figwheel_server.log"
                                    "resources/public/js/out"
                                    "resources/public/js/app.js"
                                    "resources/public/css/site.css"])
