(ns crawler.core
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clojure.core.async :refer [mult tap chan <! <!! >! >!! put! go go-loop alts! timeout dropping-buffer]])
  (:import [org.jsoup Jsoup]
           [java.net URL])
  (:gen-class))

(def visited-urls (atom #{}))
(def site-map (atom {}))

;; I've given massive buffers my two channels here because I don't want to drop
;; values. I'm not quite sure why they need to be so big, but anything smaller gives me:
;; Exception in thread "async-dispatch-1626" java.lang.AssertionError:
;;   Assert failed: No more than 1024 pending puts are allowed on a single channel. Consider using a windowed buffer.
;;   (< (.size puts) impl/MAX-QUEUE-SIZE)
(def urls-chan (chan 102400))

(def log-chan-to-mult (chan 102400))
(def log-mult (mult log-chan-to-mult))
(def log-chan (chan 102400))
(tap log-mult log-chan)

(def exit-chan (chan 1))

;; Logging
(defn log [& msgs]
  (go (>! log-chan-to-mult (s/join " " msgs))))

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

(defn remove-url-fragment [url]
  (s/replace url #"\#.*" ""))

;; Fetching/parsing pages
(defn async-get [url]
  (let [c (chan 1)]
    (http/get url #(put! c %))
    c))

(defn get-doc
  "Fetches a parsed html page from the given url and places onto a channel"
  [url]
  (go (let [{:keys [error body opts headers]} (<! (async-get url))
            content-type (:content-type headers)]
        (if (or error (not (.startsWith content-type "text/html")))
          (do (log "error fetching" url)
              false)
          (Jsoup/parse body (base-url (:url opts)))))))

(defn get-assets
  "Returns assets referenced from the given html document"
  [doc]
  (->> (.select doc "script, link, img")
       (mapcat (juxt #(.attr % "abs:href") #(.attr % "abs:src")))
       (filter string?)
       (remove empty?)))

(defn get-links
  "Given a html document, returns all urls (limited to <domain>) linked to"
  [doc domain]
  (->> (.select doc "a")
       (map #(.attr % "abs:href"))
       (remove empty?)
       (map string->url)
       (remove nil?)
       (filter #(.endsWith (.getHost %) (.getHost (URL. domain))))
       (filter #(#{"http" "https"} (.getProtocol %)))
       (map remove-url-fragment)))

;; Main event loop
(defn start-consumers
  "Spins up n go blocks to take a url from urls-chan, store its assets and then
  puts its links onto urls-chan, repeating until there are no more urls to take"
  [n domain]
  (dotimes [_ n]
    (go-loop [url (<! urls-chan)]
             (when-not (@visited-urls url)
               (log "crawling" url)
               (swap! visited-urls conj url)
               (when-let [doc (<! (get-doc url))]
                 (swap! site-map assoc url (get-assets doc))
                 (doseq [url (get-links doc domain)]
                   (go (>! urls-chan url)))))
             ;; Take the next url off the q, if 3 secs go by assume no more are coming
             (let [[value channel] (alts! [urls-chan (timeout 3000)])]
               (if (= channel urls-chan)
                 (recur value)
                 (>! exit-chan true))))))

(defn seconds-since [start-time]
  (double (/ (- (System/currentTimeMillis) start-time)
             1000)))

(defn run [domain]
  (start-logger)
  (start-consumers 40 domain)
  (log "Begining crawl of" domain)
  ;; Kick off with the first url
  (>!! urls-chan domain)
  (<!! exit-chan)
  (log "Done crawling"))

(defn -main
  "Crawls [domain] for links to assets"
  [domain]
  (let [start-time (System/currentTimeMillis)]
    (run domain)
    (println (json/write-str @site-map))
    (<!! (log "Completed after" (seconds-since start-time) "seconds"))))
