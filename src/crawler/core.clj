(ns crawler.core
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clojure.core.async :refer [chan <! <!! >! >!! put! go go-loop alts! timeout dropping-buffer]])
  (:import [org.jsoup Jsoup]
           [java.net URL])
  (:gen-class))

(def visited-urls (atom #{}))
(def site-map (atom {}))
(def urls-chan (chan (dropping-buffer 102400)))
(def log-chan (chan (dropping-buffer 102400)))
(def exit-chan (chan 1))
(def concurrency 1000)

;; Logging
(defn log [& msgs]
  (go (>! log-chan (s/join " " msgs))))

(defn start-logger []
  (go (while true
        (let [msg (<! log-chan)]
          (binding [*out* *err*]
            (println msg))))))

;; URL helpers
(defn string->url [url]
  (try
    (URL. url)
    (catch java.net.MalformedURLException e
      (log "Couldn't parse url:" url)
      nil)))

(defn base-url [url-str]
  (try
    (let [url (URL. url-str)]
      (str (.getProtocol url) "://" (.getHost url)))
    (catch Exception e
      (log "error parsing url" url-str)
      nil)))

;; Fetching/parsing pages
(defn async-get [url]
  (let [c (chan)]
    (http/get url #(put! c %))
    c))

(defn get-doc [url]
  (go (let [{:keys [error body opts headers]} (<! (async-get url))
            content-type (:content-type headers)]
        (if (or error (not (.startsWith content-type "text/html")))
          (do (log "error fetching" url)
              false)
          (Jsoup/parse body (base-url (:url opts)))))))

(defn get-assets [doc]
  (->> (.select doc "script, link, img")
       (mapcat #(conj [] (.attr % "abs:href") (.attr % "abs:src")))
       (filter string?)
       (remove empty?)))

(defn get-links [doc crawling-url]
  (->> (.select doc "a")
       (map #(.attr % "abs:href"))
       (remove empty?)
       (map string->url)
       (remove nil?)
       (filter #(.endsWith (.getHost %) (.getHost (URL. crawling-url))))
       (filter #(#{"http" "https"} (.getProtocol %)))
       (map #(s/replace % #"\#.*" ""))))

;; Main event loop
(defn start-consumers []
  (dotimes [_ concurrency]
    (go-loop [url (<! urls-chan)]
             (when-not (@visited-urls url)
               (log "crawling" url)
               (swap! visited-urls conj url)
               (when-let [doc (<! (get-doc url))]
                 (swap! site-map assoc url (get-assets doc))
                 (doseq [url (get-links doc url)]
                   (go (>! urls-chan url)))))
             (let [[value channel] (alts! [urls-chan (timeout 5000)])]
               (if (= channel urls-chan)
                 (recur value)
                 (>! exit-chan true))))))

(defn seconds-since [start-time]
  (double (/ (- (System/currentTimeMillis) start-time) 1000)))

(defn -main
  "Crawls [domain] for links to assets"
  [domain]
  (let [start-time (System/currentTimeMillis)]
    (start-logger)
    (log "Begining crawl of" domain)
    (start-consumers)
    (>!! urls-chan domain)
    (<!! exit-chan)
    (println (json/write-str @site-map))
    (<!! (log "Completed after" (seconds-since start-time) "seconds"))
    (System/exit 0)))
