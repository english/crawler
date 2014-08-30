(ns crawler.core
  (:require [clj-http.client :as http]
            [clojure.xml :as xml]
            [clojure.string :as s]
            [clojure.data.json :as json]
            [clojure.core.async :refer [chan <! >! >!! go go-loop alts! timeout thread buffer]])
  (:import [org.jsoup Jsoup])
  (:gen-class))

(def visited-urls (atom #{}))
(def site-map (atom {}))
(def urls-chan (chan 102400))
(def exit-channel (chan 1))

(defn base-url [url-str]
  (let [url (java.net.URL. url-str)]
    (str (.getProtocol url) "://" (.getHost url))))

(defn get-doc [url]
  (let [{:keys [body trace-redirects]} (http/get url)]
    (Jsoup/parse body (base-url (last trace-redirects)))))

(defn get-doc [url]
  (try
    (-> (http/get url)
        (:body)
        (Jsoup/parse (base-url url)))
    (catch Exception e
      (println "Exception fetching" url))))

(defn get-assets [doc]
  (->> (.select doc "script, link, img, a")
       (map #(or (.attr % "abs:href") (.attr % "abs:src")))
       (remove nil?)
       (filter #(or (.endsWith % ".css") (.endsWith % ".js")))))

(defn get-links [doc]
  (->> (.select doc "a")
       (map #(.attr % "abs:href"))
       (filter #(= "gocardless.com" (.getHost (java.net.URL. %))))
       (filter #(#{"http" "https"} (.getProtocol (java.net.URL. %))))
       (map #(s/replace (java.net.URL. %) #"\#.*" ""))))

(defn without-protocol [uri]
  (s/replace uri #"^(http|https)://" ""))

(defn process-page [url]
  (when-not (@visited-urls (without-protocol url))
    (swap! visited-urls conj (without-protocol url))
    (println "crawling" url)
    (when-let [doc (get-doc url)]
      (swap! site-map assoc url (get-assets doc))
      (doseq [url (get-links doc)]
        (go (>! urls-chan url))))))

(defn main-loop []
  (dotimes [_ 50]
    (go-loop [url (<! urls-chan)]
             (process-page url)
             (let [[value channel] (alts! [urls-chan (timeout 5000)])]
               (if (= channel urls-chan)
                 (recur value)
                 (>! exit-channel true))))))

(defn -main
  "Crawls [domain] for links to assets"
  [domain]
  (println "Crawling" domain)
  (main-loop)
  (go (>! urls-chan "http://gocardless.com"))
  (go (<! exit-channel)
      (println "COMPLETED")
      (spit "output.json" (json/write-str @site-map))
      (System/exit 0)))
