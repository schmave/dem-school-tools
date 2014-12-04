(ns clojure-getting-started.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [ring.adapter.jetty :as jetty]
            [com.ashafa.clutch :as couch]
            [clojure-getting-started.db :as db]
            [clj-time.core :as t]
            [environ.core :refer [env]]))

;; (def i  (couch/put-document db/db {:test "test"}))
;; (couch/get-document db/db (:_id i))

(def design-doc
  {"_id" "_design/view"
   "views" {"students" {"map" "function(doc) {
                                 if (doc.type === \"student\") {
                                   emit(doc.id, doc);
                                 }
                               }"}
            "student-swipes" {"map"
                              "function(doc) {
                                if (doc.type == \"student\") {
                                  map([doc._id, 0], doc);
                                } else if (doc.type == \"swipe\") {
                                  map([doc.post, 1], doc);
                                }
                              }"}
            "swipes" {"map"
                      "function(doc) {
                         if (doc.type == \"swipe\") {
                           emit(doc.id, doc);
                         }
                       }"}
            }
   "language" "javascript"})

(defn make-db [] (couch/put-document db/db design-doc))
(defn make-student [name]
  (couch/put-document db/db {:type :student :id 1 :name name}))

(defn get-students
  ([] (get-students nil))
  ([ids]
     (if ids
       (couch/get-view db/db "view" "students" {:keys (if (coll? ids) ids [ids])})
       (couch/get-view db/db "view" "students"))))

;; (sample-db)
(defn sample-db []
  (couch/delete-database db/db)
  (couch/create-database db/db)
  (make-db)
  (make-student "steve")
  (swipe-in 1)
  ;;(swipe-out 1)
  )

(defn swipe-in [id]
  (couch/put-document db/db {:type :swipe :id id :in-time (str (t/now))}))

(defn swipe-out [id]
  ;;(couch/get-view "view" "swipes" {:keys [id]})
  (couch/put-document db/db {:type :swipe :id id :time (str (t/now))}))

(defn splash []
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Test"})

(defroutes app
  (GET "/" [] (splash))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn -main [& [port]]
  (nrepl-server/start-server :port 7888 :handler cider-nrepl-handler)
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))

