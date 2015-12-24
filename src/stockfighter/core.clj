(ns stockfighter.core
  (:require [environ.core :as e]
            [clj-http.client :as client]
            [cheshire.core :as json]))

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



(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))
