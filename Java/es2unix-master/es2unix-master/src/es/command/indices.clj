(ns es.command.indices
  (:require [es.data.indices :as idx]
            [es.format.table :as table]
            [es.util :refer [maybe-get-in]]))

(def cols
  ['status
   'name
   'pri
   'rep
   'size
   'bytes
   'docs])

(defn indices [http args {:keys [verbose]}]
  (concat
   (if verbose
     [(map str cols)])
   (for [[nam data] (idx/indices http args)]
     [(maybe-get-in data :health :status)
      (name nam)
      (-> data :health :active_primary_shards)
      (maybe-get-in data :health :number_of_replicas)
      (table/make-cell
       {:val (maybe-get-in data :stats :total :store :size)
        :just :->})
      (maybe-get-in data :stats :total :store :size_in_bytes)
      (maybe-get-in data :stats :primaries :docs :count)])))
