(ns api-principal.infrastructure.policy-retry-worker
  (:require [integrant.core :as ig]
            [taoensso.telemere :as t]))

(defn- attempt! [repo {:keys [policy-id partner-id]}]
  (try
    ((:save-policy! repo) {:id policy-id :partner-id partner-id})
    ((:delete-pending-policy! repo) policy-id)
    (t/log! :info (str "Recovered pending policy " policy-id))
    (catch Exception e
      (t/error! ::pending-policy-retry-failed e)
      ((:record-pending-failure! repo) policy-id (.getMessage e)))))

(defn- run-loop! [running? repo interval-ms]
  (try
    (while @running?
      (try
        (doseq [pending ((:list-pending-policies repo))]
          (attempt! repo pending))
        (catch Exception e
          (t/error! ::policy-retry-loop-error e)))
      (Thread/sleep (long interval-ms)))
    (catch InterruptedException _
      (t/log! :info "Policy retry worker interrupted, shutting down."))))

(defmethod ig/init-key :worker/policy-retry [_ {:keys [repo interval-ms]}]
  (let [running? (atom true)
        thread   (Thread. ^Runnable #(run-loop! running? repo interval-ms)
                          "policy-retry-worker")]
    (.setDaemon thread true)
    (.start thread)
    (t/log! :info (str "Policy retry worker started (interval " interval-ms " ms)"))
    {:running? running? :thread thread}))

(defmethod ig/halt-key! :worker/policy-retry [_ {:keys [running? ^Thread thread]}]
  (when running? (reset! running? false))
  (when thread (.interrupt thread)))
