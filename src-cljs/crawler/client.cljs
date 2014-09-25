(ns crawler.client
  (:require [clojure.browser.repl]
            [clojure.browser.dom :as dom]
            [goog.dom :as gdom]
            [goog.events :as events])
  (:import (goog.net XhrIo WebSocket EventType)))

(defn by-id [id]
  (.getElementById js/document (name id)))

(defn create-dom [tag attrs & [value]]
  (gdom/createDom (name tag) (clj->js attrs) value))

(defn crawl-handler []
  (let [domain (.-value (by-id :domain))
        path   "/trigger"
        method "post"
        body   (str "domain=" domain)]
    (dom/append (by-id :content) (create-dom :ul {:id "url-list"}))
    (doto (XhrIo.)
      (events/listen EventType.SUCCESS dom/log-obj)
      (.send path method body))))

(defn initial-render []
  (doto (by-id :content)
    (gdom/removeChildren)
    (dom/append (create-dom :input {:type "text" :id "domain"}))
    (dom/append (create-dom :button {:onclick crawl-handler} "Crawl"))))

(defn handle-message [packet]
  (let [ul (by-id :url-list)
        message (.-message packet)]
    (dom/log message)
    (dom/append ul (create-dom :li nil message))))

(defn ^:export init []
  (doto (WebSocket.)
    (events/listen WebSocket.EventType.MESSAGE handle-message)
    (events/listen WebSocket.EventType.OPENED  #(dom/log "websocket opened"))
    (events/listen WebSocket.EventType.CLOSED  #(dom/log "websocket closed"))
    (events/listen WebSocket.EventType.ERROR   (fn [e]
                                                 (dom/log "websocket error")
                                                 (dom/log-obj e)))
    (.open "ws://localhost:3000/ws"))
  (initial-render))
