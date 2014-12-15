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
    (let [c (async/chan 1)]
      (async/>!! c (merge {:error nil
                           :body body
                           :opts {:url url}
                           :headers {:content-type "text/html"}} options))
                 c)))

(deftest test-get-page
  (testing "happy path"
    (let [html "<html><body><a href=\"/page\">Link to page</a></body></html>"]
      (with-redefs [crawler.core/async-get (mock-async-get html)]
        (is (= "http://example.com/page"
               (-> (get-page "http://example.com")
                   (async/<!!)
                   (.select "a")
                   (first)
                   (.attr "abs:href")))))))

  (testing "with an error response"
    (with-redefs [crawler.core/async-get (mock-async-get "" {:error true})]
      (is (= false
             (async/<!! (get-page "http://example.com"))))))

  (testing "when responding with non-html"
    (with-redefs [crawler.core/async-get
                  (mock-async-get "" {:headers {:content-type "application/json"}})]
      (is (= false
             (async/<!! (get-page "http://example.com")))))))

(deftest test-get-assets
  (testing "images"
    (let [html "<html><body><img src=\"/image.png\" /></body></html>"
          page (Jsoup/parse html "http://example.com")]
      (is (= '("http://example.com/image.png")
             (get-assets page)))))

  (testing "scripts"
    (let [html "<html><body><script src=\"/script.js\"></script></body></html>"
          page (Jsoup/parse html "http://example.com")]
      (is (= '("http://example.com/script.js")
             (get-assets page)))))

  (testing "css"
    (let [html "<html><link rel=\"stylesheet\" href=\"style.css\" /></html>"
          page (Jsoup/parse html "http://example.com")]
      (is (= '("http://example.com/style.css")
             (get-assets page))))))

(defn html-page [{:keys [head body]}]
  (str "<html>"
         "<head>" head "</head>"
         "<body>" body "</body>"
       "</html"))

(deftest test-get-links
  (testing "empty link"
    (let [domain "http://example.com"
          page (Jsoup/parse (html-page {:body "<a href=\"\"></a>"}) domain)]
      (is (= '("http://example.com")
             (get-links page domain)))))

  (testing "bad link"
    (let [domain "http://example.com"
          page (Jsoup/parse
                 (html-page {:body "<a href=\"bla://example.com/\"></a>"})
                 domain)]
      (is (= '()
             (get-links page domain)))))

  (testing "relative link"
    (let [domain "http://example.com"
          page (Jsoup/parse (html-page {:body "<a href=\"page\"></a>"}) domain)]
      (is (= '("http://example.com/page")
             (get-links page domain)))))

  (testing "relative link"
    (let [domain "http://example.com"
          page (Jsoup/parse (html-page {:body "<a href=\"page\"></a>"}) domain)]
      (is (= '("http://example.com/page")
             (get-links page domain)))))

  (testing "link with domain"
    (let [domain "http://example.com"
          page (Jsoup/parse
                 (html-page {:body "<a href=\"http://example.com/page\"></a>"})
                 domain)]
      (is (= '("http://example.com/page")
             (get-links page domain)))))

  (testing "link with other domain"
    (let [domain "http://example.com"
          page (Jsoup/parse
                 (html-page
                   {:body "<a href=\"http://other.com/page\"></a>"})
                 domain)]
      (is (= '()
             (get-links page domain)))))

  (testing "multiple links"
    (let [domain "http://example.com"
          body "<a href=\"http://other.com/page\"></a>
               <a href=\"/absolute-iink\"></a>
               <a href=\"relative-link\"></a>
               <a href=\"http://example.com/with-domain-link\"></a>"
          page (Jsoup/parse (html-page {:body body}) domain)]
      (is (= '("http://example.com/absolute-iink"
                "http://example.com/relative-link"
                "http://example.com/with-domain-link")
             (get-links page domain))))))

(deftest test-run
  (testing "it works"
    (let [html "<html>
                 <head><script src=\"script.js\"></script></head>
                 <body><a href=\"/page\">a link</a></body>
               </html>"]
      (with-redefs [crawler.core/async-get (mock-async-get html)]
        (is (= (run "http://example.com" (async/chan 1))
               {"http://example.com/page" '("http://example.com/script.js")
                "http://example.com"      '("http://example.com/script.js")}))))))
