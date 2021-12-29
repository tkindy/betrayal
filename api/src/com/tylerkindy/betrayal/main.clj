(ns com.tylerkindy.betrayal.main
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [reitit.ring :as r]))

(defn handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Hello, World!"})

(defonce server (atom nil))

(def app
  (r/ring-handler
   (r/router
    ["/" {:get handler}])
   (constantly {:status 404})))

(defn start-server! [join?]
  (reset! server
          (run-jetty app
                     {:port 3000
                      :join? join?})))

(defn stop-server! []
  (let [server @server]
    (when server (.stop server))))

(defn restart-server! []
  (stop-server!)
  (start-server! false))

(defn -main []
  (start-server! true))
