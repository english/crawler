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
      (timbre/info "Couldn't parse url:" url)
      nil)))

(defn base-url [url-str]
  (try
    (let [url (URL. url-str)]
      (str (.getProtocol url) "://" (.getHost url)))
    (catch Exception e
      (timbre/error "error parsing url" url-str e)
      nil)))

(defn remove-url-fragment [url]
  (s/replace url #"\#.*" ""))

;; Fetching/parsing pages
(defn async-get [url]
  (let [c (async/chan 1)]
    (http/get url #(async/put! c %))
    c))

(defn get-page
  "Fetches a parsed html page from the given url and places onto a channel"
  [url]
  (async/go (let [{:keys [error body opts headers]} (async/<! (async-get url))
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
       (filter string?)
       (remove empty?)))

(defn get-links
  "Given a html document, returns all urls (limited to <domain>) linked to"
  [page domain]
  (->> (.select page "a")
       (map #(.attr % "abs:href"))
       (map string->url)
       (remove nil?)
       (filter #(.endsWith (.getHost %) (.getHost (URL. domain))))
       (filter #(#{"http" "https"} (.getProtocol %)))
       (map remove-url-fragment)))

;; Main event loop
(defn start-consumers
  "Spins up n go blocks to take a url from urls-chan, store its assets and then
  puts its links onto urls-chan, repeating until there are no more urls to take"
  [n domain urls-chan sitemap progress-chan]
  (let [visited-urls (atom #{})
        exit-chan (async/chan 1)]
    (dotimes [_ n]
      (async/go-loop []
        (let [[url channel] (async/alts! [urls-chan (async/timeout 3000)])]
          (if (not= channel urls-chan)
            (async/>! exit-chan true)
            (when-not (nil? url)
              (when-not (@visited-urls url)
                (timbre/info "crawling" url)
                (swap! visited-urls conj url)
                (when-let [page (async/<! (get-page url))]
                  (swap! sitemap assoc url (get-assets page))
                  (async/>! progress-chan [url (get-assets page)])
                  (async/onto-chan urls-chan (get-links page domain) false)))
              (recur))))))
    exit-chan))

(defn seconds-since [start-time]
  (double (/ (- (System/currentTimeMillis) start-time)
             1000)))

(defn run [domain progress-chan]
  (let [urls-chan (async/chan 102400)
        sitemap (atom {})]
    (let [workers-done-chan (start-consumers 40 domain urls-chan sitemap progress-chan)]
      (timbre/info "Begining crawl of" domain)
      ;; Kick off with the first url
      (async/>!! urls-chan domain)
      (async/<!! workers-done-chan)
      (timbre/info "Done crawling")
      @sitemap)))

(defn -main
  "Crawls [domain] for links to assets"
  [domain]
  (let [start-time (System/currentTimeMillis)]
    (println (json/write-str (run domain (async/chan))))
    (timbre/info "Completed after" (seconds-since start-time) "seconds")))
