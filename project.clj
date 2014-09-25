(defproject crawler "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [http-kit "2.1.16"]
                 [org.clojure/data.json "0.2.5"]
                 [org.jsoup/jsoup "1.7.1"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [org.clojure/clojurescript "0.0-2311"]
                 [compojure "1.1.8"]
                 [ring "1.3.1"]
                 [ring/ring-devel "1.1.8"]
                 [org.clojure/tools.logging "0.3.1"]
                 [enlive "1.1.5"]]
  :dev-dependencies [[javax.servlet/servlet-api "2.5"]]
  :source-paths ["src" "src-cljs"]
  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]
            [com.cemerick/austin "0.1.5"]]
  :cljsbuild {:builds [{:id "crawler"
                        :source-paths ["src-cljs"]
                        :compiler {:output-to "resources/public/js/app.js"
                                   :output-dir "resources/public/js/out"
                                   :optimizations :none
                                   :source-map true}}]}
  :main ^:skip-aot crawler.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
