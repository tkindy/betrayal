(ns betrayal.spike.dev
  (:gen-class)
  (:require [betrayal.spike.main :as main]
            [ring.middleware.reload :refer [wrap-reload]]))

(def app
  (wrap-reload #'main/app {:dirs ["src"]}))

(defn -main [& _]
  (main/start-server! #'app))
