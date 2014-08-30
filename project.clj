(defproject crawler "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "1.0.0"]
                 [org.clojure/data.json "0.2.5"]
                 [org.jsoup/jsoup "1.7.1"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]]
  :main ^:skip-aot crawler.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
