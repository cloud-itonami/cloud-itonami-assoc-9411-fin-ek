#!/usr/bin/env nbb
;; scripts/verify-catalog.cljs — check the EK catalog against its own sources.
;;
;;   nbb scripts/verify-catalog.cljs            structural only (offline)
;;   nbb scripts/verify-catalog.cljs --live     also fetch every :url and require
;;                                              every :source-quote to be in it
;;
;; Ported from cloud-itonami-assoc-9411-sau-fsc (which took it from -gbr-cbi).
;; The three corrections that repo measured are carried verbatim, because the
;; conditions that produced them are properties of nbb and ClojureScript, not
;; of that catalog: `clojure.string` rather than `kotoba.lang.text` (nothing
;; here puts that namespace on the classpath, and a gate whose documented
;; invocation cannot start is not a weaker gate, it is no gate); `[\s\S]*?`
;; rather than `(?is)` for the script/style strip (cljs gives `(?s)` no dotall
;; flag, so multi-line <style> blocks survive and widen the haystack a quote is
;; searched in -- which can only turn a fabricated citation into a passing one);
;; and a data path that must end in `.edn` (so `--classpath DIR` is not read as
;; the catalog and REFUSED as unreadable).
;;
;; What is NOT carried: `provenance->archived-origin`. fsc.org.sa went off the
;; air and every entry there is a Wayback snapshot. ek.fi serves all of its own
;; pages directly -- measured 2026-09-11, ten of ten sources returned 2xx from
;; their first-party hosts -- so the archived-origin machinery would be a rule
;; with nothing to check. The keys stay absent rather than empty: an empty map
;; reads as "checked, none archived" and an absent one reads as "this catalog
;; has no archived sources", which is the true statement.
;;
;; Exit codes are three-valued on purpose:
;;
;;   0  checked, nothing wrong
;;   1  checked, findings printed
;;   2  REFUSED -- could not check. Not 0, because "I could not read the
;;      catalog" and "I read the catalog and it was fine" must not leave the
;;      same trace, and not 1, because there is no finding to act on.
;;
;; Why :source-quote exists at all: reachability is not support. A URL that
;; returns HTTP 200 and does not contain the claim looks exactly like a URL
;; that does, so a citation can rot without anyone noticing. --live does not
;; ask whether the citation resolves; it asks whether the document still says
;; the thing the entry says it says.
;;
;; That distinction was not theoretical while this catalog was being built. A
;; search result offered a finlex.fi PDF as the official English translation of
;; the Associations Act (503/1989); the URL returned 200, and the PDF is the
;; Act on the Finnish Red Cross (238/2000). Reachable, first-party, wrong
;; document. It is not in this catalog because the text was read, and reading
;; the text is what this script automates.

(ns verify-catalog
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            ["fs" :as fs]
            ["os" :as os]
            ["path" :as path]
            ["child_process" :as cp]))

;; process.argv holds this script's own path. Dropping a fixed count gets it
;; wrong the moment the launcher changes, and the symptom is that the script
;; path becomes the catalog path -- which this script then reports as
;; unreadable, i.e. a refusal that looks like a broken catalog.
(def argv (vec (remove #(str/ends-with? % "verify-catalog.cljs")
                       (drop 2 (js->clj (.-argv js/process))))))
(def live? (some #{"--live"} argv))
(def data-path
  (or (first (filter #(str/ends-with? % ".edn") argv)) "data/datascript-tx.edn"))

(def ASSOCIATION "ek")
(def ISIC "9411")
(def COUNTRY "FIN")

;; A provenance keyword names WHO is speaking. The host is the only thing in
;; the entry that can corroborate it, so the two are declared together here and
;; an unlisted keyword is a finding rather than a pass -- a new source has to
;; say whose site it is, instead of inheriting authority for free.
(def provenance->host
  {:official-ek-fi            "ek.fi"
   :official-businesseurope-eu "businesseurope.eu"
   ;; Two entries rest on Wikipedia, in two different language editions. The
   ;; host is the registrable domain rather than `en.wikipedia.org`, because
   ;; the check accepts a subdomain of the declared host and the EK founding
   ;; date is only stated in full on the Finnish article. Naming the language
   ;; edition here would make `fi.` a finding without making anything safer:
   ;; both editions are the same speaker for the purpose this keyword records,
   ;; which is that the claim is NOT first-party.
   :wikipedia-corroborated    "wikipedia.org"})

(def en-months
  ["january" "february" "march" "april" "may" "june"
   "july" "august" "september" "october" "november" "december"])

(defn- ordinal [n]
  (let [suffix (cond (#{11 12 13} (mod n 100)) "th"
                     (= 1 (mod n 10)) "st"
                     (= 2 (mod n 10)) "nd"
                     (= 3 (mod n 10)) "rd"
                     :else "th")]
    (str n suffix)))

(defn refuse! [msg]
  (println (str "REFUSED: " msg))
  (println "Refusing to report a pass on a catalog this run could not read.")
  (.exit js/process 2))

(defn- read-catalog []
  (let [txt (try (fs/readFileSync data-path "utf8")
                 (catch :default e (refuse! (str data-path ": " (.-message e)))))
        data (try (edn/read-string txt)
                  (catch :default e (refuse! (str data-path " is not readable EDN: "
                                                 (.-message e)))))]
    (when-not (vector? data)
      (refuse! (str data-path " is not a vector of entries (got "
                    (if (nil? data) "nil" (type data)) ")")))
    (when (empty? data)
      ;; An empty catalog satisfies every per-entry assertion below. Without
      ;; this floor, deleting the catalog would be reported as a clean run.
      (refuse! (str data-path " holds no entries; every per-entry check would "
                    "be vacuously true")))
    [txt data]))

(def date-re #"^\d{4}(-\d{2})?(-\d{2})?$")

(defn- precision-of [d]
  (case (count (str/split (str d) #"-")) 3 :day 2 :month 1 :year nil))

(defn- host-of [u]
  (when-let [m (re-matches #"^https://([^/]+)/.*$" (str u))]
    (str/replace (nth m 1) #"^www\." "")))

(defn- date-named-in?
  "Does `q` name `d` at `prec`, the way these sources write dates? ISO is
   accepted too, so a source that does write ISO is not forced into words.
   A month-precise date must name BOTH the month and the year: this catalog's
   pages write `March 1980` inside a parenthesis after a Hijri date, and the
   year alone appears in several unrelated spans on the same page."
  [q d prec]
  (let [q (str/lower-case q)
        [y m dd] (str/split (str d) #"-")
        month (when m (get en-months (dec (js/parseInt m 10))))
        day-forms (when dd (let [n (js/parseInt dd 10)]
                             (distinct [dd (str n) (ordinal n)])))]
    (or (str/includes? q (str/lower-case (str d)))
        (case prec
          :day (boolean (some (fn [df]
                                (some #(str/includes? q %)
                                      [(str df " " month " " y)
                                       (str df " " month ", " y)
                                       (str month " " df ", " y)
                                       (str month " " df " " y)]))
                              day-forms))
          :month (and (str/includes? q month) (str/includes? q y))
          :year (str/includes? q y)
          false))))

(defn- structural [data]
  (let [ids (map :association-rule/id data)
        dups (->> ids frequencies (keep (fn [[k n]] (when (< 1 n) k))) sort)]
    (concat
     (for [d dups] [:duplicate-id (str d " appears " (count (filter #{d} ids)) " times")])
     (mapcat
      (fn [[i e]]
        (let [at (fn [k] (get e (keyword "association-rule" k)))
              where (str "entry " i " (" (or (at "id") "<no id>") ")")
              f (fn [tag msg] [tag (str where ": " msg)])]
          (concat
           (when-not (string? (at "id")) [(f :missing-key ":id is missing or not a string")])
           (when (and (string? (at "id"))
                      (not (str/starts-with? (at "id") (str ASSOCIATION "."))))
             [(f :id-shape (str ":id must start with \"" ASSOCIATION ".\""))])
           (when-not (and (string? (at "title")) (seq (at "title")))
             [(f :missing-key ":title is missing or empty")])
           (when-not (= ASSOCIATION (at "association"))
             [(f :missing-key (str ":association must be " ASSOCIATION))])
           (when-not (= ISIC (at "isic")) [(f :missing-key (str ":isic must be " ISIC))])
           (when-not (= COUNTRY (at "country")) [(f :missing-key (str ":country must be " COUNTRY))])
           (when-not (keyword? (at "kind")) [(f :missing-key ":kind must be a keyword")])
           (when-not (and (string? (at "url")) (str/starts-with? (at "url") "https://"))
             [(f :url-shape ":url must be an https:// URL")])
           (when-not (keyword? (at "url-provenance"))
             [(f :missing-key ":url-provenance must be a keyword")])
           ;; The provenance keyword and the URL must name the same speaker.
           (when (keyword? (at "url-provenance"))
             (let [p (at "url-provenance")
                   expected (provenance->host p)
                   h (host-of (at "url"))]
               (cond
                 (nil? expected)
                 [(f :provenance-host
                     (str p " is not a declared provenance; add it to "
                          "provenance->host with the host it speaks for, or use "
                          "one of: " (str/join ", " (sort (map str (keys provenance->host))))))]
                 (nil? h)
                 [(f :provenance-host (str "cannot read a host out of :url " (at "url")))]
                 (not (or (= h expected) (str/ends-with? h (str "." expected))))
                 [(f :provenance-host
                     (str p " claims " expected " but :url is served by " h))]
                 :else nil)))
           (when-not (and (string? (at "source-article")) (seq (at "source-article")))
             [(f :missing-key ":source-article is missing or empty")])
           (when-not (and (string? (at "source-quote")) (seq (at "source-quote")))
             [(f :missing-key (str ":source-quote is missing or empty -- an entry with "
                                   "no quote cannot be checked against its own source"))])
           (when-not (and (vector? (at "topic")) (seq (at "topic")))
             [(f :missing-key ":topic must be a non-empty vector")])
           (for [d [(at "established-date") (at "last-revised-date") (at "retrieved-at")]
                 :when (and (some? d) (not (re-matches date-re (str d))))]
             (f :date-shape (str "not an ISO date: " d)))
           ;; A date the source does not give may be omitted -- but only out
           ;; loud. An entry that is simply missing both dates and one that
           ;; records why it has none must not read the same.
           (when-not (or (at "established-date") (at "last-revised-date")
                         (at "date-unknown-because"))
             [(f :missing-key (str "needs :established-date or :last-revised-date, "
                                   "or :date-unknown-because naming why the source "
                                   "gives neither"))])
           (when (and (at "date-unknown-because")
                      (not (keyword? (at "date-unknown-because"))))
             [(f :missing-key ":date-unknown-because must be a keyword")])
           (when (and (at "date-unknown-because")
                      (or (at "established-date") (at "last-revised-date")))
             [(f :date-shape (str ":date-unknown-because is set on an entry that "
                                  "does carry a date"))])
           ;; How precisely the source dates the fact is a property of the
           ;; source, not of the reader. Twelve of these fourteen entries carry
           ;; no date because the page states a standing arrangement and does
           ;; not date it; one is dated only in Hijri. Recording that per entry
           ;; is what lets the judgement be checked instead of recounted.
           (let [d (or (at "established-date") (at "last-revised-date"))
                 p (at "date-precision")]
             (cond
               (and (nil? d) p)
               [(f :date-precision ":date-precision is set on an entry with no date")]
               (and d (nil? p))
               [(f :date-precision (str "needs :date-precision (" (name (precision-of d))
                                        ") saying how precisely the source dates it"))]
               (and d p (not= p (precision-of d)))
               [(f :date-precision (str ":date-precision " p " does not match " d
                                        " (" (name (precision-of d)) ")"))]
               ;; The quote must name the date. A span that is on the page but
               ;; does not carry this date supports some other entry, not this
               ;; one -- and reads identically until asked.
               (and d p (string? (at "source-quote"))
                    (not (date-named-in? (at "source-quote") d p)))
               [(f :date-not-in-quote
                   (str ":source-quote does not name " d " (" (name p)
                        "); the span is not evidence for this entry's date"
                        "\n      quote: " (at "source-quote")))]
               :else nil)))))
      (map-indexed vector data)))))

(def ua "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")

(defn- have? [bin]
  (try (cp/execFileSync "sh" #js ["-c" (str "command -v " bin)] #js {:stdio "ignore"}) true
       (catch :default _ false)))

;; Named entities this decoder knows. It is deliberately not the whole HTML5
;; table: what matters here is the punctuation these sources are made of. Every
;; page in this catalog is WordPress, which writes the curly apostrophe as a
;; numeric reference, and four of the fourteen spans below contain one. An
;; incomplete decoder does not weaken this check, it inverts it -- the verbatim
;; span of a page that IS serving the claim comes back as not found, which is
;; exactly how a fabricated citation looks.
(def ^:private named-entities
  {"nbsp" " " "quot" "\"" "apos" "'" "lt" "<" "gt" ">"
   "aacute" "á" "eacute" "é" "iacute" "í" "oacute" "ó" "uacute" "ú"
   "Aacute" "Á" "Eacute" "É" "Iacute" "Í" "Oacute" "Ó" "Uacute" "Ú"
   "agrave" "à" "egrave" "è" "igrave" "ì" "ograve" "ò" "ugrave" "ù"
   "acirc" "â" "ecirc" "ê" "icirc" "î" "ocirc" "ô" "ucirc" "û"
   "auml" "ä" "euml" "ë" "iuml" "ï" "ouml" "ö" "uuml" "ü"
   "ntilde" "ñ" "atilde" "ã" "otilde" "õ" "ccedil" "ç" "Ccedil" "Ç"
   "sect" "§" "deg" "°" "middot" "·" "pound" "£" "euro" "€"
   "laquo" "«" "raquo" "»" "bdquo" "„" "ldquo" "“" "rdquo" "”"
   "lsquo" "‘" "rsquo" "’" "sbquo" "‚"
   "ndash" "–" "mdash" "—" "hellip" "…"})

(defn- decode-entities
  "HTML entities -> characters. `&amp;` is decoded LAST, so that a document
   that literally writes `&amp;ndash;` keeps saying `&ndash;` rather than
   silently becoming a dash."
  [s]
  (-> s
      (str/replace #"&#(\d+);"
                   (fn [[_ d]] (js/String.fromCodePoint (js/parseInt d 10))))
      (str/replace #"&#[xX]([0-9a-fA-F]+);"
                   (fn [[_ h]] (js/String.fromCodePoint (js/parseInt h 16))))
      (str/replace #"&([a-zA-Z][a-zA-Z0-9]{1,9});"
                   (fn [[whole nm]] (get named-entities nm whole)))
      (str/replace #"&amp;" "&")))

(defn- fetch-text
  "Returns [status text] or [status nil] -- nil text means the body arrived but
   this run could not turn it into text, which is a refusal, not a finding."
  [url]
  (let [tmp (path/join (os/tmpdir) (str "ek-src-" (hash url)))
        status (try (str/trim (str (cp/execFileSync
                                    "curl" #js ["-sS" "-L" "--max-time" "180"
                                                "--compressed" "-A" ua "-o" tmp
                                                "-w" "%{http_code}" url]
                                    #js {:encoding "utf8"})))
                    (catch :default e (str "curl-failed: " (.-message e))))
        body (try (fs/readFileSync tmp) (catch :default _ nil))
        pdf? (and body (str/starts-with? (.toString (.slice body 0 5) "utf8") "%PDF-"))
        text (cond
               (nil? body) nil
               pdf? (when (have? "pdftotext")
                      (try (str (cp/execFileSync "pdftotext" #js [tmp "-"]
                                                 #js {:encoding "utf8"
                                                      :maxBuffer 33554432}))
                           (catch :default _ nil)))
               :else (-> (.toString body "utf8")
                         ;; [\s\S] rather than (?s). See the header: cljs does
                         ;; not turn (?s) into a dotall JS flag, so `.` stops at
                         ;; a newline and multi-line <style> blocks survive.
                         (str/replace #"<(script|style|noscript)[^>]*>[\s\S]*?</\1>" " ")
                         (str/replace #"<[^>]+>" " ")
                         decode-entities))]
    (try (fs/unlinkSync tmp) (catch :default _ nil))
    [status (when text (str/replace text #"\s+" " "))]))

(defn- run-live [data]
  (when-not (have? "curl") (refuse! "curl is not on PATH"))
  (let [urls (vec (distinct (map :association-rule/url data)))
        fetched (reduce (fn [m u] (assoc m u (fetch-text u))) {} urls)
        unreadable (for [[u [status text]] fetched
                         :when (or (not (re-matches #"2\d\d" status)) (nil? text))]
                     (str u " -> status=" status
                          (when (nil? text) " (body could not be turned into text)")))]
    (println (str "FETCHED\t" (- (count urls) (count unreadable)) "/" (count urls)))
    (when (seq unreadable)
      ;; Every quote check below would be "not found", which reads exactly like
      ;; a fabricated citation. Refuse instead of accusing the catalog.
      (refuse! (str "could not read " (count unreadable) " of " (count urls)
                    " sources:\n  " (str/join "\n  " unreadable))))
    (keep (fn [e]
            (let [u (:association-rule/url e)
                  q (str/replace (str (:association-rule/source-quote e)) #"\s+" " ")
                  [_ text] (get fetched u)]
              (when-not (str/includes? text q)
                [:quote-not-in-source
                 (str (:association-rule/id e) ": :source-quote is not in " u
                      "\n      quote: " q)])))
          data)))

(let [[txt data] (read-catalog)
      findings (concat (structural data) (when live? (run-live data)))]
  (println (str "SCANNED\t" (count data) " entries, "
                (count (re-seq #"https?://" txt)) " citations, "
                (count (distinct (map :association-rule/url data))) " distinct sources"
                (if live? ", live" ", structural only")))
  (doseq [[tag msg] findings] (println (str "  [" (name tag) "] " msg)))
  (if (seq findings)
    (do (println (str (count findings) " finding(s)")) (.exit js/process 1))
    (do (println "ok") (.exit js/process 0))))
