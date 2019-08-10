(defproject axiecrush "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [re-pressed "0.3.0"]
                 [district0x/re-frame-interval-fx "1.0.2"]
                 [camel-snake-kebab "0.4.0"]
                 [cljs-ajax "0.7.4"]
                 [funcool/cuerdas "2.2.0"]
                 [reagent "0.8.1"]
                 [re-frame "0.10.8"]
                 [clj-commons/secretary "1.2.4"]
                 [venantius/accountant "0.2.4"]]

  :plugins [[lein-cljsbuild "1.1.7"]]

  :min-lein-version "2.5.3"

  :source-paths ["src/clj" "src/cljs"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]


  :figwheel {:css-dirs ["resources/public/css"]
             :server-port 1234
             :ring-handler "axiecrush.core/handler"}

  :profiles
  {:dev
   {:dependencies [[binaryage/devtools "0.9.10"]]
    :source-paths ["src" "test"]
    :plugins      [[lein-figwheel "0.5.18"]]}

   :prod { }
   }

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs"]
     :figwheel     {:on-jsload "axiecrush.core/mount-root"
                    :websocket-host :js-client-host}
     :compiler     {:main                 axiecrush.core
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "/js/compiled/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload]
                    :external-config      {:devtools/config {:features-to-install :all}}
                    }}

    {:id           "min"
     :source-paths ["src/cljs"]
     :compiler     {:main            axiecrush.core
                    :output-to       "resources/public/js/compiled/app.js"
                    :optimizations   :advanced
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}


    ]}
  )
