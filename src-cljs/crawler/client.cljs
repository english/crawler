(ns crawler.client
  (:require [clojure.browser.repl]
            [clojure.browser.dom :as dom]
            [goog.dom :as gdom]
            [goog.events :as events]
            [goog.json :as gjson])
  (:import (goog.net XhrIo WebSocket EventType)))

;; Make NodeList seq-able
(extend-type js/NodeList
  ISeqable
  (-seq [array] (array-seq array 0)))

(extend-type js/HTMLCollection
  ISeqable
  (-seq [array] (array-seq array 0)))

(defn by-id [id]
  (.getElementById js/document (name id)))

(defn create-dom [tag attrs & values]
  (apply gdom/createDom (name tag) (clj->js attrs) values))

(defn crawl-handler [ws]
  (let [domain (.-value (by-id :domain))]
    (dom/append (by-id :content) (create-dom :ul {:id "url-list"}))
    (.send ws domain)))

(defn initial-render [ws]
  (doto (by-id :content)
    (gdom/removeChildren)
    (dom/append (create-dom :input {:type "text" :id "domain" :value "https://gocardless.com"}))
    (dom/append (create-dom :button {:onclick (partial crawl-handler ws)} "Crawl"))))

(defn render-list [values]
  (apply create-dom :ul nil
         (map (partial create-dom :li nil) values)))

(defn handle-message [packet]
  (let [ul (by-id :url-list)
        [url assets] (js->clj (gjson/parse (.-message packet)))]
    (dom/append ul
                (create-dom :li {:className "inactive"
                                 :onclick (fn []
                                            (this-as this
                                                     (set! (.-className this) (if (= "active" (.-className this))
                                                                                "inactive" "active"))))}
                            (create-dom :span nil url)
                            (create-dom :span {:className "asset-count"} (str (count assets)))
                            (render-list assets)))))

(defn ^:export init []
  (doto (WebSocket.)
    (events/listen WebSocket.EventType.MESSAGE handle-message)
    (events/listen WebSocket.EventType.OPENED  #(dom/log "websocket opened"))
    (events/listen WebSocket.EventType.CLOSED  #(dom/log "websocket closed"))
    (events/listen WebSocket.EventType.ERROR   (fn [e]
                                                 (dom/log "websocket error")
                                                 (dom/log-obj e)))
    (.open "ws://localhost:3000/ws")
    (initial-render)))
