(ns crawler.core-test
  (:require [clojure.test :refer :all]
            [crawler.core :refer :all]
            [clojure.core.async :as async])
  (:import [org.jsoup Jsoup]))

(deftest test-remove-url-fragment
  (testing "url with a fragment"
    (is (= "http://example.com" (remove-url-fragment "http://example.com#thing"))))
  (testing "url without a fragment"
    (is (= "http://example.com" (remove-url-fragment "http://example.com")))))

(defn mock-async-get [body & [options]]
  (fn [url]
    (let [c (async/chan 10)]
      (async/>!! c (merge {:error nil
                           :body body
                           :opts {:url url}
                           :headers {:content-type "text/html"}} options))
                 c)))

(deftest test-get-doc
  (testing "happy path"
    (let [html "<html><body><a href=\"/page\">Link to page</a></body></html>"]
      (with-redefs [crawler.core/async-get (mock-async-get html)]
        (is (= "http://example.com/page"
               (-> (get-doc "http://example.com")
                   (async/<!!)
                   (.select "a")
                   (first)
                   (.attr "abs:href")))))))

  (testing "with an error response"
    (with-redefs [crawler.core/async-get (mock-async-get "" {:error true})]
      (is (= false
             (async/<!! (get-doc "http://example.com"))))))

  (testing "when responding with non-html"
    (with-redefs [crawler.core/async-get
                  (mock-async-get "" {:headers {:content-type "application/json"}})]
      (is (= false
             (async/<!! (get-doc "http://example.com")))))))

(deftest test-get-assets
  (testing "images"
    (let [html "<html><body><img src=\"/image.png\" /></body></html>"
          doc (Jsoup/parse html "http://example.com")]
      (is (= '("http://example.com/image.png")
             (get-assets doc)))))

  (testing "scripts"
    (let [html "<html><body><script src=\"/script.js\"></script></body></html>"
          doc (Jsoup/parse html "http://example.com")]
      (is (= '("http://example.com/script.js")
             (get-assets doc)))))

  (testing "css"
    (let [html "<html><link rel=\"stylesheet\" href=\"style.css\" /></html>"
          doc (Jsoup/parse html "http://example.com")]
      (is (= '("http://example.com/style.css")
             (get-assets doc))))))

(deftest test-get-links
  (testing "simple"
    (let [domain "http://example.com"
          html "<html><a href=\"page\"></a></html>"]
      (is (= '("http://example.com/page")
             (get-links (Jsoup/parse html domain) domain))))))

(deftest test-run
  (testing "it works"
    (let [html "<html><script src=\"script.js\"></script><body><a href=\"/page\">a link</a></body></html>"]
      (with-redefs [crawler.core/async-get (mock-async-get html)]
        (is (= (run "http://example.com")
               {"http://example.com/page" '("http://example.com/script.js"),
                "http://example.com"      '("http://example.com/script.js")}))))))
