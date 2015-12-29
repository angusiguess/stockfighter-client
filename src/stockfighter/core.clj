(ns stockfighter.core
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as cm]
            [aleph.http :as http]
            [manifold.stream :as s]
            [clojure.core.async :as a]))

(defn common-opts []
  {})

(defmacro def-api-action [name args verb uri opts]
  `(defn ~name
     [~@args]
     (client/request (merge ~opts {:method ~verb
                                   :url (format ~uri ~@args)
                                   :as :json}))))

(defmacro def-api-with-body [name [body & args] uri opts]
  `(defn ~name
     [~@args ~body]
     (client/request (merge ~opts {:method :post
                                   :url (format ~uri ~@args)
                                   :as :json
                                   :content-type :json
                                   :form-params ~body}))))

(defn init [api-key mgr-opts]
  (let [mgr (cm/make-reusable-conn-manager (or mgr-opts {}))
        common-opts {:connection-manager mgr
                     :as :json
                     :content-type :json
                     :headers {"X-Starfighter-Authorization" api-key}}]
    (def-api-action api-up? [] :get "https://api.stockfighter.io/ob/api/heartbeat"
      common-opts)
    (def-api-action venue-up? [venue] :get "https://api.stockfighter.io/ob/api/venues/%s/heartbeat"
      common-opts)
    (def-api-action get-stocks [venue] :get "https://api.stockfighter.io/ob/api/venues/%s/stocks"
      common-opts)
    (def-api-action get-book [venue stock] :get "https://api.stockfighter.io/ob/api/venues/%s/stocks/%s"
      common-opts)
    (def-api-action get-quote [venue stock] :get "https://api.stockfighter.io/ob/api/venues/%s/stocks/%s/quote"
      common-opts)
    (def-api-action get-order [venue stock order] :get "https://api.stockfighter.io/ob/api/venues/%s/stocks/%s/orders/%s"
      common-opts)
    (def-api-action cancel-order [venue stock order] :delete "https://api.stockfighter.io/ob/api/venues/%s/stocks/%s/orders/%s"
      common-opts)
    (def-api-action all-orders [venue account] :get "https://api.stockfighter.io/ob/api/venues/accounts/%s/orders"
      common-opts)
    (def-api-action orders-by-stock [venue account stock] :get "https://api.stockfighter.io/ob/api/venues/accounts/%s/orders"
      common-opts)
    (def-api-with-body post-order [body venue stock] "https://api.stockfighter.io/ob/api/venues/%s/stocks/%s/orders"
      common-opts)
    (def-api-action get-level [level] :post "https://www.stockfighter.io/gm/levels/%s"
      common-opts)
    (def-api-action stop-level [instance] :post "https://www.stockfighter.io/gm/instances/%s/stop"
      common-opts)
    (def-api-action resume-level [instance] :post "https://www.stockfighter.io/gm/instances/%s/resume"
      common-opts)
    (def-api-action get-instance [instance] :get "https://www.stockfighter.io/gm/instances/%s"
      common-opts)))

(defn quotes [account venue]
  (let [uri (format "wss://api.stockfighter.io/ob/api/ws/%s/venues/%s/tickertape" account venue)
        quotes> (a/chan 1024)]
    (if-let [socket (try @(http/websocket-client uri)
                         (catch Exception e (.printStackTrace e)
                                e))]
      (do (s/connect (s/map identity socket) quotes>)
          {:chan quotes>
           :close (delay (.close socket)
                         (a/close! quotes>))})
      (println "Nope"))))

(defn fills [account venue]
  (let [uri (format "wss://api.stockfighter.io/ob/api/ws/%s/venues/%s/executions" account venue)
        fills> (a/chan 1024)]
    (if-let [socket (try @(http/websocket-client uri)
                         (catch Exception e (.printStacktrace e)
                                (println (.getMessage e))
                                e))]
      (do (s/connect (s/map identity socket) fills>)
          {:chan fills>
           :close (delay (.close socket)
                         (a/close! fills>))})
      (println "Nope"))))
