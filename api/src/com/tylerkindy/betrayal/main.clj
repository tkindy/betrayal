(ns com.tylerkindy.betrayal.main
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [reitit.ring :as r]
            [reitit.coercion.malli :as c]))

(defn handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Hello, World!"})

(def app
  (r/ring-handler
   (r/router
    ["/api" {:coercion c/coercion}
     ["" {:get handler}]
     ["/games/{game-id}" {:get (fn [_] {:status 200})}]])
   (r/routes
    (r/redirect-trailing-slash-handler {:method :strip})
    (r/create-default-handler))))

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
