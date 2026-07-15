(ns construction.motionplan-test
  "construction.motionplan/motion-plan-for -- the Cartesian waypoint list
  built from construction.robotics/mission-actions's real 3-step
  sequence (ADR-2607997500). Direct port of fab.motionplan-test's/
  quarryops.motionplan-test's own assertions, adapted to construction's
  own mission-actions/site shape."
  (:require [clojure.test :refer [deftest is testing]]
            [construction.cad :as cad]
            [construction.motionplan :as motionplan]
            [construction.robotics :as robotics]))

(deftest one-waypoint-per-mission-action-same-order
  (let [plan (motionplan/motion-plan-for {:cylinder-height-actual-mm 300.0
                                           :cylinder-diameter-actual-mm 150.0})]
    (is (= (count robotics/mission-actions) (count plan)))
    (is (= (mapv :step robotics/mission-actions) (mapv :step plan)))
    (is (= [1 2 3] (mapv :seq plan)))
    (is (= ["rebar-placement-scan" "as-built-total-station-survey" "concrete-cure-test-cylinder-press"]
           (mapv :station plan)))))

(deftest waypoints-are-spaced-along-the-travel-axis
  (let [plan (motionplan/motion-plan-for {:cylinder-height-actual-mm 300.0})
        xs (mapv #(first (:waypoint %)) plan)]
    (is (= [0.0 motionplan/station-pitch-m (* 2 motionplan/station-pitch-m)] xs))
    (is (every? #(= motionplan/default-tool-orientation (:tool-orientation %)) plan))
    (is (every? #(zero? (second (:waypoint %))) plan) "y is the line centerline")))

(deftest working-height-derives-from-the-sites-real-envelope
  (testing "z (working height) is half the site's own real cylinder-height envelope"
    (let [site {:cylinder-height-actual-mm 400.0}
          plan (motionplan/motion-plan-for site)
          z (nth (:waypoint (first plan)) 2)]
      (is (= (/ 400.0 2000.0) z))))
  (testing "a site with no real :cylinder-height-actual-mm still gets a real answer
            via construction.cad's own disclosed ASTM/EN standard default, not
            motionplan's separate fallback"
    (let [plan (motionplan/motion-plan-for {})
          z (nth (:waypoint (first plan)) 2)]
      (is (= (/ cad/default-cylinder-height-mm 2000.0) z))))
  (testing "no site at all (older/hand-rolled caller) -> motionplan's own default-working-height-m"
    (let [plan (motionplan/motion-plan-for)
          z (nth (:waypoint (first plan)) 2)]
      (is (= motionplan/default-working-height-m z)))))

(deftest deterministic-same-site-same-plan
  (is (= (motionplan/motion-plan-for {:cylinder-height-actual-mm 400.0})
         (motionplan/motion-plan-for {:cylinder-height-actual-mm 400.0}))))
