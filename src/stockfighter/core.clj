(ns stockfighter.core
  (:require [environ.core :as e]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [aleph.http :as http]
            [manifold.stream :as s]
            [clojure.core.async :as a]))

(def api-key (e/env :api-key))

(defn common-opts []
  {:as :json
   :headers {"X-Starfighter-Authorization" api-key}})

(defn api-up? []
  (let [response (client/get "https://api.stockfighter.io/ob/api/heartbeat" (common-opts))]
    (get-in response [:body :ok])))

(defn venue-up? [venue]
  (let [uri (format "https://api.stockfighter.io/ob/api/venues/%s/heartbeat" venue)
        response (client/get uri (common-opts))]
    (get-in response [:body :ok])))

(defn get-stocks [venue]
  (let [uri (format "https://api.stockfighter.io/ob/api/venues/%s/stocks" venue)
        response (client/get uri (common-opts))]
    (if (get-in response [:body :ok])
      (get-in response [:body :symbols])
      nil)))

(defn get-book [venue stock]
  (let [uri (format "https://api.stockfighter.io/ob/api/venues/%s/stocks/%s" venue stock)
        response (client/get uri (common-opts))]
    (if (get-in response [:body :ok])
      (get-in response [:body :bids]))))

(defn make-order [venue stock payload]
  (let [uri (format "https://api.stockfighter.io/ob/api/venues/%s/stocks/%s/orders" venue stock)
        opts (-> (common-opts)
                 (assoc :form-params (merge payload {:venue venue
                                                     :stock stock}))
                 (assoc :content-type :json))
        response (client/post uri opts)]
    (get response :body)))

(defn get-quote [venue stock]
  (let [uri (format "https://api.stockfighter.io/ob/api/venues/%s/stocks/%s/quote" venue stock)
        response (client/get uri (common-opts))]
    (get response :body)))

(defn orders-by-account [venue account]
  (let [uri (format "https://api.stockfighter.io/ob/api/venues/%s/accounts/%s/orders" venue account)
        response (client/get uri (common-opts))]
    (get response :body)))

(defn orders-by-stock-and-account [venue account stock]
  (let [uri (format "https://api.stockfighter.io/ob/api/venues/%s/accounts/%s/stocks/%s/orders"
                    venue
                    account
                    stock)
        response (client/get uri (common-opts))]
    (get response :body)))

(defn get-order-status [venue account stock order-id]
  (let [uri (format "https://api.stockfighter.io/ob/api/venues/%s/stocks/%s/orders/%s"
                    venue
                    account
                    stock
                    order-id)
        response (client/get uri (common-opts))]
    (get response :body)))

(defn cancel-order [venue account stock order-id]
  (let [uri (format "https://api.stockfighter.io/ob/api/venues/%s/stocks/%s/orders/%s"
                    venue
                    account
                    stock
                    order-id)
        response (client/get uri (common-opts))]
    (get response :body)))

(defn quotes [account venue]
  (let [uri (format "wss://api.stockfighter.io/ob/api/ws/%s/venues/%s/tickertape" account venue)
        quotes> (a/chan 1024)]
    (if-let [socket (try @(http/websocket-client uri)
                         (catch Exception e (.printStackTrace e)
                                e))]
      (do (s/connect (s/map json/parse-string  socket) quotes>)
          {:chan quotes>
           :close (delay (.close socket)
                         (a/close! quotes>))})
      (println "Nope"))))

(defn fills [account venue]
  (let [uri (format "wss://api.stockfighter.io/ob/api/ws/%s/venues/%s/executions" account venue)
        fills> (a/chan 1024)]
    (if-let [socket (try @(http/websocket-client uri)
                         (catch Exception e (.printStacktrace e)
                                e))]
      (do (println socket)
          (s/connect (s/map json/parse-string socket) fills>)
          {:chan fills>
           :close (delay (.close socket)
                         (a/close! fills>))}))))
