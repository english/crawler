(ns crawler.core
  (:require [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [clojure.string :as s]
            [taoensso.timbre :as timbre :refer [info error]]
            [clojure.data.json :as json]
            [clojure.core.async :refer [mult tap chan <! <!! >! >!! put! go go-loop alts! timeout dropping-buffer buffer close! onto-chan]])
  (:import [org.jsoup Jsoup]
           [java.net URL])
  (:gen-class))

;; URL helpers
(defn string->url [url]
  (try
    (URL. url)
    (catch java.net.MalformedURLException e
      (error "Couldn't parse url:" url)
      nil)))

(defn base-url [url-str]
  (try
    (let [url (URL. url-str)]
      (str (.getProtocol url) "://" (.getHost url)))
    (catch Exception e
      (error "error parsing url" url-str e)
      nil)))

(defn remove-url-fragment [url]
  (s/replace url #"\#.*" ""))

;; Fetching/parsing pages
(defn async-get [url]
  (let [c (chan 1)]
    (http/get url #(put! c %))
    c))

;(defn async-get [url]
  ;(let [c (chan 2)]
    ;(>!! c {:body "<html>
                          ;<script src=\"script.js\"></script>
                          ;<link rel=\"stylesheet\" href=\"style.css\"></link>
                          ;<body>
                            ;<a href=\"/page1\">a link</a>
                            ;<a href=\"/page2\">a link</a>
                          ;</body>
                        ;</html>"
                  ;:headers {:content-type "text/html"}
                  ;:opts {:url url}})
    ;c))

(defn get-page
  "Fetches a parsed html page from the given url and places onto a channel"
  [url]
  (go (let [{:keys [error body opts headers]} (<! (async-get url))
            content-type (:content-type headers)]
        (if (or error (not (.startsWith content-type "text/html")))
          (do (timbre/error "error fetching" url error)
              false)
          (Jsoup/parse body (base-url (:url opts)))))))

(defn get-assets
  "Returns assets referenced from the given html document"
  [page]
  (->> (.select page "script, link, img")
       (mapcat (juxt #(.attr % "abs:href") #(.attr % "abs:src")))
       (filter string?)
       (remove empty?)))

(defn get-links
  "Given a html document, returns all urls (limited to <domain>) linked to"
  [page domain]
  (->> (.select page "a")
       (map #(.attr % "abs:href"))
       #_(remove empty?)
       (map string->url)
       #_(remove nil?)
       (filter #(.endsWith (.getHost %) (.getHost (URL. domain))))
       (filter #(#{"http" "https"} (.getProtocol %)))
       (map remove-url-fragment)))

;; Main event loop
(defn start-consumers
  "Spins up n go blocks to take a url from urls-chan, store its assets and then
  puts its links onto urls-chan, repeating until there are no more urls to take"
  [n domain urls-chan visited-urls sitemap progress-chan]
  (let [exit-chan (chan 1)]
    (dotimes [_ n]
      (go-loop []
        (let [[url channel] (alts! [urls-chan (timeout 3000)])]
          (if (not= channel urls-chan)
            (>! exit-chan true)
            (when-not (nil? url)
              (when-not (@visited-urls url)
                (info "crawling" url)
                (swap! visited-urls conj url)
                (when-let [page (<! (get-page url))]
                  (swap! sitemap assoc url (get-assets page))
                  (>! progress-chan [url (get-assets page)])
                  (onto-chan urls-chan (get-links page domain) false)))
              (recur))))))
    exit-chan))

(defn seconds-since [start-time]
  (double (/ (- (System/currentTimeMillis) start-time)
             1000)))

(defn run [domain progress-chan]
  (let [urls-chan (chan 102400)
        visited-urls (atom #{})
        sitemap (atom {})]
    (let [workers-done-chan (start-consumers 40 domain urls-chan visited-urls sitemap progress-chan)]
      (info "Begining crawl of" domain)
      ;; Kick off with the first url
      (>!! urls-chan domain)
      (<!! workers-done-chan)
      (info "Done crawling")
      @sitemap)))

(defn -main
  "Crawls [domain] for links to assets"
  [domain]
  (let [start-time (System/currentTimeMillis)]
    (println (json/write-str (run domain)))
    (info "Completed after" (seconds-since start-time) "seconds")))
