(ns crawler.server
  (:require [org.httpkit.server :refer :all]
            [ring.util.response :refer [file-response]]
            [ring.middleware.reload :refer [wrap-reload]]
            [compojure.route :refer [resources not-found]]
            [compojure.handler :refer [site]]
            [compojure.core :refer [defroutes GET POST DELETE ANY context]]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as enlive]
            [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [crawler.core :as crawler]
            [clojure.core.async :as async]))

(def log-chan (async/chan 102400))
(async/tap crawler/log-mult log-chan)

(enlive/deftemplate index
  (io/resource "public/index.html")
  []
  [:body] (enlive/append
            (enlive/html [:script (browser-connected-repl-js)])))

(defn ws-handler [request]
  (with-channel request channel
    (on-close channel #(println "channel closed: " %))
    (async/go-loop []
                   (when-some [v (async/<! log-chan)]
                     (send! channel v)
                     (recur)))))

(def mock-chan (async/chan (async/sliding-buffer 10)))

(defn mock-trigger [domain]
  (let [end-chan (async/timeout 10000)]
    (async/go-loop [i 0]
                   (let [[v c] (async/alts! [(async/timeout 200) end-chan])]
                     (when-not (= c end-chan)
                       (async/>! mock-chan (str domain i))
                       (recur (inc i))))))
  "SUCCESS")

(defn ws-mock [request]
  (with-channel request channel
      (async/go-loop []
                     (when-some [v (async/<! mock-chan)]
                       (send! channel v)
                       (recur)))))

(defroutes all-routes
  (GET "/" req (index))
  (GET "/ws" [] ws-handler)
  (GET "/mock-ws" [] ws-mock)
  (POST "/mock-trigger" [domain] (mock-trigger domain))
  (POST "/trigger" [domain]
        (async/go (crawler/run domain))
        "Success")
  (resources "/")
  (not-found "<p>Page not found</p>"))

(defonce server (atom nil))

(defn app []
  (-> (site #'all-routes)
      wrap-reload
      (run-server {:port 3000})))

(defn start-server []
  (swap! server (fn [_] (app))))

(defn kill-server []
  (@server))

(comment (start-server)
         (kill-server))
