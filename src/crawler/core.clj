(ns crawler.core
  (:require [clj-http.client :as http]
            [clojure.xml :as xml]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clojure.core.async :refer [chan <! <!! >! >!! go go-loop alts! timeout buffer]])
  (:import [org.jsoup Jsoup]
           [java.net URL])
  (:gen-class))

(def visited-urls (atom #{}))
(def site-map (atom {}))
(def urls-chan (chan 102400))
(def log-chan (chan))
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
  (let [url (URL. url-str)]
    (str (.getProtocol url) "://" (.getHost url))))

;; Fetching/parsing pages
(defn get-doc [url]
  (try
    (let [{:keys [body trace-redirects]} (http/get url)]
      (Jsoup/parse body (base-url (last trace-redirects))))
    (catch Exception e
      (log "Exception fetching" url)
      nil)))

(defn get-assets [doc]
  (->> (.select doc "script, link, img, a")
       (mapcat #(conj [] (.attr % "abs:href") (.attr % "abs:src")))
       (filter string?)
       (filter #(or (.endsWith % ".css") (.endsWith % ".js")))))

(defn get-links [doc crawling-url]
  (->> (.select doc "a")
       (map #(.attr % "abs:href"))
       (remove empty?)
       (map string->url)
       (remove nil?)
       (filter #(.endsWith (.getHost %) (.getHost (URL. crawling-url))))
       (filter #(#{"http" "https"} (.getProtocol %)))
       (map #(s/replace % #"\#.*" ""))))

(defn process-page [url]
  (when-not (@visited-urls url)
    (swap! visited-urls conj url)
    (log "crawling" url)
    (when-let [doc (get-doc url)]
      (swap! site-map assoc url (get-assets doc))
      (doseq [url (get-links doc url)]
        (go (>! urls-chan url))))))

;; Main event loop
(defn start-consumers []
  (dotimes [_ concurrency]
    (go-loop [url (<! urls-chan)]
             (process-page url)
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
