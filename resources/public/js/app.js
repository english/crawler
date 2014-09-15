goog.addDependency("base.js", ['goog'], []);
goog.addDependency("../cljs/core.js", ['cljs.core'], ['goog.string', 'goog.object', 'goog.string.StringBuffer', 'goog.array']);
goog.addDependency("../clojure/browser/event.js", ['clojure.browser.event'], ['cljs.core', 'goog.events.EventTarget', 'goog.events.EventType', 'goog.events']);
goog.addDependency("../clojure/browser/net.js", ['clojure.browser.net'], ['goog.net.xpc.CfgFields', 'goog.net.XhrIo', 'goog.json', 'goog.Uri', 'cljs.core', 'goog.net.EventType', 'goog.net.xpc.CrossPageChannel', 'clojure.browser.event']);
goog.addDependency("../clojure/browser/repl.js", ['clojure.browser.repl'], ['cljs.core', 'clojure.browser.net', 'clojure.browser.event']);
goog.addDependency("../clojure/browser/dom.js", ['clojure.browser.dom'], ['goog.dom', 'cljs.core', 'goog.object']);
goog.addDependency("../crawler/client.js", ['crawler.client'], ['goog.dom', 'goog.net.XhrIo', 'clojure.browser.repl', 'cljs.core', 'clojure.browser.dom', 'goog.net.EventType', 'goog.net.WebSocket', 'goog.events']);