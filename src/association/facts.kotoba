(ns association.facts
  "Industry rule/history catalog for EK (Elinkeinoelämän
  keskusliitto, Confederation of Finnish Industries) -- a 61st
  industry-association-level source (see
  cloud-itonami-assoc-9411-sau-fsc, -9411-aut-wko, -9411-irl-ibec,
  -9411-nzl-businessnz, -9411-cze-spcr, -9411-ind-cii, -9411-zaf-busa,
  -9411-bra-cni, -9411-ken-kam, -9411-can-chamber, -9411-mex-coparmex,
  -9411-ita-confindustria, -9411-nld-vnoncw, -9411-kor-kcci,
  -9411-arg-uia, -9411-bel-feb, -9411-dnk-di, -9411-swe-sn for the
  first eighteen) per ADR-2607141700 (cloud-itonami-compliance-fact-
  federation). The NINETEENTH entry aligned to ISIC 9411 (activities
  of business, employers, and professional membership organizations).
  Fills Finland's previously-open association-axis gap (one of the
  9-country gap list recorded at tick 153) -- Finland now has real,
  individually verified facts across ALL THREE axes (country:
  cloud-itonami-iso3166-fin statute.facts; municipality:
  cloud-itonami-municipality-fin-helsinki; association: this entry).

  ek.fi's own domain did not surface a navigable founding-history
  page this tick (main page rendered but had no historical content, a
  guessed history-page URL 404'd). Both entries here were instead
  directly WebFetch-verified: en.wikipedia.org's own article quotes
  verbatim 'The history of the Confederation of Finnish Industries
  dates back to 1907, when employers established the General
  Employers' Confederation of Finland' (earliest predecessor); and
  fi.wikipedia.org's own article quotes verbatim (Finnish) 'aloitti
  toimintansa vuoden 2005 alussa, kun Palvelutyönantajat (lyhenne PT)
  ja Teollisuuden ja Työnantajain Keskusliitto (TT) yhdistyivät'
  (began operations at the start of 2005, when Palvelutyönantajat (PT)
  and Teollisuuden ja Työnantajain Keskusliitto (TT) merged) -- EK
  itself. The 2005 date is independently corroborated by Wikidata
  Q5159811's own 'inception' statement. Note en.wikipedia.org's own
  article is itself categorized 'Organizations established in 2005'
  even though its body text stops at the intermediate 1992 TT merger
  without explaining the 2005 EK formation -- the more complete
  fi.wikipedia.org account was used to fill this gap rather than
  guessing. No personal names of office-holders are persisted here.

  An association not in `catalog` has NO spec-basis, full stop; never
  fabricate one.")

(def catalog
  "association-slug -> vector of association-rule entries."
  {"ek"
   [{:association-rule/id "ek.predecessor-1907-general-employers-confederation"
     :association-rule/title "The General Employers' Confederation of Finland, EK's earliest predecessor, established 1907 (en.wikipedia.org)"
     :association-rule/association "ek"
     :association-rule/isic "9411"
     :association-rule/country "FIN"
     :association-rule/kind :governance-program
     :association-rule/url "https://en.wikipedia.org/wiki/Confederation_of_Finnish_Industries"
     :association-rule/url-provenance :wikipedia-corroborated
     :association-rule/established-date "1907"
     :association-rule/retrieved-at "2026-07-17"
     :association-rule/topic #{:governance}}
    {:association-rule/id "ek.founding-2005-pt-tt-merger"
     :association-rule/title "EK began operations at the start of 2005, formed via merger of Palvelutyönantajat (PT) and Teollisuuden ja Työnantajain Keskusliitto (TT) (fi.wikipedia.org, corroborated by Wikidata Q5159811 inception statement)"
     :association-rule/association "ek"
     :association-rule/isic "9411"
     :association-rule/country "FIN"
     :association-rule/kind :governance-program
     :association-rule/url "https://fi.wikipedia.org/wiki/Elinkeinoel%C3%A4m%C3%A4n_keskusliitto"
     :association-rule/url-provenance :wikipedia-corroborated
     :association-rule/established-date "2005"
     :association-rule/retrieved-at "2026-07-17"
     :association-rule/topic #{:governance}}]})

(defn spec-basis [association] (get catalog association))

(defn coverage
  ([] (coverage (keys catalog)))
  ([associations]
   (let [have (filter catalog associations)
         missing (remove catalog associations)]
     {:requested (count associations)
      :covered (count have)
      :covered-associations (vec (sort have))
      :missing-associations (vec (sort missing))
      :note (str "cloud-itonami-assoc-9411-fin-ek Wave 0 (ADR-2607141700): "
                 (count (get catalog "ek")) " EK entries seeded "
                 "with en.wikipedia.org + fi.wikipedia.org + Wikidata Q5159811 corroboration "
                 "(ek.fi had no navigable history page this tick). "
                 "Extend `association.facts/catalog`, never fabricate an id/url.")})))

(defn by-topic [association topic]
  (filterv #(contains? (:association-rule/topic %) topic) (spec-basis association)))
