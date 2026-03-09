(ns msync.lucene.schema
  (:require [malli.core :as m]
            [malli.error :as me]))

(def ^:private removed-indexing-option-keys
  #{:indexed-fields :stored-fields :keyword-fields :suggest-fields :context-fn})

(def ^:private field-type-schema
  [:enum :text :keyword])

(def ^:private context-source-schema
  [:fn
   {:error/message "must be a field keyword, a sequence of field keywords, or a function"}
   (fn [value]
     (or (nil? value)
         (keyword? value)
         (and (sequential? value)
              (every? keyword? value))
         (fn? value)))])

(def ^:private suggest-schema
  [:map
   [:weight {:optional true} pos-int?]
   [:contexts-from {:optional true} context-source-schema]])

(def ^:private field-schema
  [:map
   [:type field-type-schema]
   [:indexed? {:optional true} :boolean]
   [:stored? {:optional true} :boolean]
   [:multi-valued? {:optional true} :boolean]
   [:suggest {:optional true} suggest-schema]])

(def ^:private indexing-options-schema
  [:map
   [:fields [:map-of :keyword field-schema]]])

(def ^:private valid-indexing-options?
  (m/validator indexing-options-schema))

(defn- fail!
  [message data]
  (throw (ex-info message data)))

(defn- normalize-suggest-spec
  [{:keys [weight contexts-from]
    :or {weight 1}}]
  {:weight weight
   :contexts-from contexts-from})

(defn- normalize-field-spec
  [field-name {:keys [type indexed? stored? multi-valued? suggest]}]
  (let [normalized-field-spec {:type          type
                               :indexed?      (if (nil? indexed?) true indexed?)
                               :stored?       (if (nil? stored?) true stored?)
                               :multi-valued? (if (nil? multi-valued?) false multi-valued?)
                               :suggest       (some-> suggest normalize-suggest-spec)}]
    (when-not (or (:indexed? normalized-field-spec)
                  (:stored? normalized-field-spec)
                  (:suggest normalized-field-spec))
      (fail! "Every field must be stored, indexed, or suggest-enabled"
             {:field-name field-name
              :field-spec normalized-field-spec}))
    normalized-field-spec))

(defn -normalize-indexing-options
  "Validate and normalize the public indexing options into canonical field specs."
  [{:keys [fields] :as indexing-options}]
  (when-let [removed-options (not-empty (select-keys indexing-options removed-indexing-option-keys))]
    (fail! "index! no longer accepts bucketed field options; define fields under :fields"
           {:removed-options (keys removed-options)
            :example         {:fields {:title {:type :text
                                               :stored? true
                                               :indexed? true}}}}))
  (when-not (valid-indexing-options? indexing-options)
    (fail! "index! requires a :fields map of canonical field specs"
           {:indexing-options indexing-options
            :errors           (me/humanize (m/explain indexing-options-schema indexing-options))}))
  {:fields (into {}
                 (map (fn [[field-name field-spec]]
                        [field-name (normalize-field-spec field-name field-spec)]))
                 fields)})
