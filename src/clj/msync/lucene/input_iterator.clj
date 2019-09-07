(ns msync.lucene.input-iterator
  (:require [msync.lucene.utils :refer [string->bytes-ref]])
  (:import [msync.lucene_internal MapDocsInputIterator]))

(def not-nil? (complement nil?))
(defn init [coll
            {:keys [text-field
                    context-fn
                    payload-fn
                    weight-fn]}]
  (let [coll      (into [] coll)
        payload?  (not-nil? payload-fn)
        contexts? (not-nil? context-fn)
        weight-fn (or weight-fn (constantly 1))]
    (atom {:v-coll     coll
           :index      0
           :count      (count coll)
           :current    nil
           :text-field text-field
           :payload-fn payload-fn
           :context-fn context-fn
           :weight-fn  weight-fn
           :payload?   payload?
           :contexts?  contexts?})))

(defn weight [state]
  (let [{:keys [current weight-fn]} (deref state)]
    (weight-fn current)))

(defn payload? [state]
  (-> state deref :payload?))

(defn payload [state]
  (let [{:keys [payload-fn current]} (deref state)]
    (payload-fn current)))

(defn contexts? [state]
  (-> state deref :contexts?))

(defn contexts [state]
  (let [{:keys [context-fn current]} (deref state)
        contexts (context-fn current)]
    (->> contexts
         (map string->bytes-ref)
         (into #{}))))

(defn next-item [state]
  (let [-state (deref state)
        {:keys [v-coll index count text-field]} -state]
    (if (< index count)
      (let [current (get v-coll index)]
        (swap! state assoc :index (inc index) :current current)
        (-> current
            text-field
            pr-str
            string->bytes-ref)))))

(def ^:private actions-map
  {:weight    weight
   :payload?  payload?
   :payload   payload
   :contexts? contexts?
   :contexts  contexts
   :next      next-item})

(defn doc-maps->input-iterator [doc-maps & {:keys [text-field context-fn payload-fn weight-fn]
                                            :or   {weight-fn (constantly 1)}}]
  (let [state (init doc-maps {:text-field text-field
                              :context-fn context-fn
                              :payload-fn payload-fn
                              :weight-fn  weight-fn})]
    (MapDocsInputIterator. actions-map state)))