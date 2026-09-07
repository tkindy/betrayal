(ns betrayal.spike.db-test
  (:require [betrayal.spike.db :as db]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]))

(deftest constructs-datasource-configuration
  (with-redefs [jdbc/get-datasource identity]
    (testing "a complete JDBC URL is used directly"
      (is (= {:jdbcUrl "jdbc:postgresql://localhost/example?user=alex"}
             (db/datasource
              {"JDBC_DATABASE_URL"
               "jdbc:postgresql://localhost/example?user=alex"}))))

    (testing "separate production settings use the JDBC user property"
      (is (= {:jdbcUrl "jdbc:postgresql://database:5432/betrayal"
              :user "betrayal"
              :password "secret"}
             (db/datasource {"DB_HOST" "database"
                             "DB_NAME" "betrayal"
                             "DB_USER" "betrayal"
                             "DB_PASSWORD" "secret"}))))))
