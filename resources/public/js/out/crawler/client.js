// Compiled by ClojureScript 0.0-2311
goog.provide('crawler.client');
goog.require('cljs.core');
goog.require('goog.dom');
goog.require('goog.net.WebSocket');
goog.require('clojure.browser.dom');
goog.require('goog.net.EventType');
goog.require('clojure.browser.repl');
goog.require('clojure.browser.dom');
goog.require('goog.events');
goog.require('goog.dom');
goog.require('goog.net.XhrIo');
goog.require('goog.events');
crawler.client.by_id = (function by_id(id){return document.getElementById(cljs.core.name.call(null,id));
});
/**
* @param {...*} var_args
*/
crawler.client.create_dom = (function() { 
var create_dom__delegate = function (tag,attrs,p__5405){var vec__5407 = p__5405;var value = cljs.core.nth.call(null,vec__5407,(0),null);return goog.dom.createDom(cljs.core.name.call(null,tag),cljs.core.clj__GT_js.call(null,attrs),value);
};
var create_dom = function (tag,attrs,var_args){
var p__5405 = null;if (arguments.length > 2) {
  p__5405 = cljs.core.array_seq(Array.prototype.slice.call(arguments, 2),0);} 
return create_dom__delegate.call(this,tag,attrs,p__5405);};
create_dom.cljs$lang$maxFixedArity = 2;
create_dom.cljs$lang$applyTo = (function (arglist__5408){
var tag = cljs.core.first(arglist__5408);
arglist__5408 = cljs.core.next(arglist__5408);
var attrs = cljs.core.first(arglist__5408);
var p__5405 = cljs.core.rest(arglist__5408);
return create_dom__delegate(tag,attrs,p__5405);
});
create_dom.cljs$core$IFn$_invoke$arity$variadic = create_dom__delegate;
return create_dom;
})()
;
crawler.client.crawl_handler = (function crawl_handler(){var domain = crawler.client.by_id.call(null,new cljs.core.Keyword(null,"domain","domain",1847214937)).value;var path = "/trigger";var method = "post";var body = ("domain="+cljs.core.str.cljs$core$IFn$_invoke$arity$1(domain));var G__5410 = (new goog.net.XhrIo());goog.events.listen(G__5410,goog.net.EventType.SUCCESS,clojure.browser.dom.log_obj);
G__5410.send(path,method,body);
return G__5410;
});
crawler.client.render = (function render(){var G__5412 = crawler.client.by_id.call(null,new cljs.core.Keyword(null,"content","content",15833224));goog.dom.removeChildren(G__5412);
clojure.browser.dom.append.call(null,G__5412,crawler.client.create_dom.call(null,new cljs.core.Keyword(null,"input","input",556931961),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"type","type",1174270348),"text",new cljs.core.Keyword(null,"id","id",-1388402092),"domain"], null)));
clojure.browser.dom.append.call(null,G__5412,crawler.client.create_dom.call(null,new cljs.core.Keyword(null,"button","button",1456579943),new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"onclick","onclick",1297553739),crawler.client.crawl_handler], null),"Crawl"));
return G__5412;
});
crawler.client.init = (function init(){var G__5415_5416 = (new goog.net.WebSocket());goog.events.listen(G__5415_5416,goog.net.WebSocket.EventType.MESSAGE,((function (G__5415_5416){
return (function (p1__5413_SHARP_){return clojure.browser.dom.log.call(null,p1__5413_SHARP_.message);
});})(G__5415_5416))
);
goog.events.listen(G__5415_5416,goog.net.WebSocket.EventType.OPENED,((function (G__5415_5416){
return (function (){return clojure.browser.dom.log.call(null,"websocket opened");
});})(G__5415_5416))
);
goog.events.listen(G__5415_5416,goog.net.WebSocket.EventType.CLOSED,((function (G__5415_5416){
return (function (){return clojure.browser.dom.log.call(null,"websocket closed");
});})(G__5415_5416))
);
goog.events.listen(G__5415_5416,goog.net.WebSocket.EventType.ERROR,((function (G__5415_5416){
return (function (e){clojure.browser.dom.log.call(null,"websocket error");
return clojure.browser.dom.log_obj.call(null,e);
});})(G__5415_5416))
);
G__5415_5416.open("ws://localhost:3000/ws");
return crawler.client.render.call(null);
});
goog.exportSymbol('crawler.client.init', crawler.client.init);

//# sourceMappingURL=client.js.map