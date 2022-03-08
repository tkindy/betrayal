(ns com.tylerkindy.betrayal.main
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [reitit.ring :as rr]))

(defn handler [request]
  (let [name (get-in request [:params :name])]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str "Hello, " (or name "World") "!")}))

(def router
  (rr/router
   ["/"
    ["" {:get handler}]
    ["games/{game-id}" {:get (fn [_] {:status 200})}]]))

(def app
  (-> router
      rr/ring-handler
      (wrap-json-body {:keywords? true})
      wrap-json-response
      (wrap-defaults api-defaults)))

(defn start-server [join?]
  (run-jetty app {:port 3000
                  :join? join?}))

(defonce server (atom nil))
(defn reload! []
  (let [server @server]
    (when server (.stop server)))
  (reset! server (start-server false)))

(defn -main []
  (start-server true))
