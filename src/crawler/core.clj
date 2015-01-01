(ns crawler.core
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.string :as s]
            [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.core.async :as async])
  (:import [org.jsoup Jsoup]
           [java.net URL])
  (:gen-class))

;; URL helpers
(defn string->url [url]
  (try
    (io/as-url url)
    (catch java.net.MalformedURLException e
      (timbre/info "Couldn't parse url:" url))))

(defn base-url [url-str]
  (when-let [url (string->url url-str)]
    (str (.getProtocol url) "://" (.getHost url))))

(defn remove-url-fragment [url-str]
  (s/replace url-str #"\#.*" ""))

;; Fetching/parsing pages
(defn async-get [url]
  (let [c (async/chan 1)]
    (http/get url {:keepalive 30000} #(async/put! c %))
    c))

(defn get-page
  "Fetches a parsed html page from the given url and places onto a channel"
  [url]
  (async/go
    (let [{:keys [error body opts headers]} (async/<! (async-get url))
          content-type (:content-type headers)]
      (if (or error (not (.startsWith content-type "text/html")))
        (timbre/error "error fetching" url error)
        (Jsoup/parse body (base-url (:url opts)))))))

(defn get-assets
  "Returns assets referenced from the given html document"
  [page]
  (->> (.select page "script, link, img")
       (mapcat (juxt #(.attr % "abs:href")
                     #(.attr % "abs:src")))
       (remove s/blank?)))

(defn same-domain?
  "True if url-a is on the same domain (or a subdomain of) url-b"
  [url-a url-b]
  (.endsWith (.getHost url-b) (.getHost url-a)))

(defn get-links
  "Given a html document, returns all urls (limited to domain) linked to"
  [page]
  (->> (.select page "a")
       (map #(.attr % "abs:href"))
       (map string->url)
       (remove nil?)
       (filter (partial same-domain? (string->url (.baseUri page))))
       (filter #(#{"http" "https"} (.getProtocol %)))
       (map remove-url-fragment)))

;; Main event loop
(defn start-consumers
  "Spins up n go blocks to take a url from urls-chan, store its assets and then
  puts its links onto urls-chan, repeating until there are no more urls to take"
  [n urls-chan progress-chan]
  (let [visited-urls (atom #{})]
    (for [_ (range n)]
      (async/go-loop [sitemap {}]
        (let [[url channel] (async/alts! [urls-chan (async/timeout 1000)])]
          (if (or (not= channel urls-chan) ; timeout happened
                  (nil? url)) ; urls-chan has been closed
            (do (async/close! progress-chan)
                (async/close! urls-chan)
                sitemap)
            (if (@visited-urls url)
              (recur sitemap)
              (do (timbre/info "->" url)
                  (swap! visited-urls conj url)
                  (if-let [page (async/<! (get-page url))]
                    (do (timbre/info "<-" url)
                        (let [assets (get-assets page)
                              links (get-links page)]
                          (async/>! progress-chan [url assets])
                          (async/onto-chan urls-chan links false)
                          (recur (assoc sitemap url assets))))
                    (recur sitemap))))))))))

(defn seconds-since [start-time]
  (double (/ (- (System/currentTimeMillis) start-time)
             1000)))

(defn run
  ([domain progress-chan]
   (let [urls-chan (async/chan 1)
         worker-channels (start-consumers 200 urls-chan progress-chan)]
     (timbre/info "Begining crawl of" domain)
     ;; Kick off with the first url
     (async/>!! urls-chan domain)
     (async/reduce merge {} (async/merge worker-channels))))
  ([domain]
   (run domain (async/chan (async/dropping-buffer 1)))))

(defn -main
  "Crawls [domain] for links to assets"
  [domain]
  (let [start-time (System/currentTimeMillis)]
    (println (json/write-str (async/<!! (run domain))))
    (timbre/info "Completed after" (seconds-since start-time) "seconds")))
