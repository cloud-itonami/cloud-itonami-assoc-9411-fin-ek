# cloud-itonami-assoc-9411-fin-ek

Industry rule/history catalog for **EK** (Elinkeinoelämän
keskusliitto, Confederation of Finnish Industries) — an
entry aligned to **ISIC 9411** (activities of business, employers, and
professional membership organizations), alongside
[`-9411-sau-fsc`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-sau-fsc)
(Saudi Arabia),
[`-9411-aut-wko`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-aut-wko)
(Austria),
[`-9411-irl-ibec`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-irl-ibec)
(Ireland),
[`-9411-nzl-businessnz`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-nzl-businessnz)
(New Zealand),
[`-9411-cze-spcr`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-cze-spcr)
(Czech Republic),
[`-9411-ind-cii`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-ind-cii)
(India),
[`-9411-zaf-busa`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-zaf-busa)
(South Africa),
[`-9411-bra-cni`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-bra-cni)
(Brazil),
[`-9411-ken-kam`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-ken-kam)
(Kenya),
[`-9411-can-chamber`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-can-chamber)
(Canada),
[`-9411-mex-coparmex`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-mex-coparmex)
(Mexico),
[`-9411-ita-confindustria`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-ita-confindustria)
(Italy),
[`-9411-nld-vnoncw`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-nld-vnoncw)
(Netherlands),
[`-9411-kor-kcci`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-kor-kcci)
(South Korea),
[`-9411-arg-uia`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-arg-uia)
(Argentina),
[`-9411-bel-feb`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-bel-feb)
(Belgium),
[`-9411-dnk-di`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-dnk-di)
(Denmark), and
[`-9411-swe-sn`](https://github.com/cloud-itonami/cloud-itonami-assoc-9411-swe-sn)
(Sweden). Part of the
[`cloud-itonami`](https://github.com/cloud-itonami) compliance-fact
family (ADR-2607141700, `cloud-itonami-compliance-fact-federation`,
in `com-junkawasaki/root`).

## Sourcing note

This repo fills Finland's previously-open association-axis gap (one
of the 9-country gap list recorded at tick 153). Finland now has
real, individually verified facts across all three axes: country
([`cloud-itonami-iso3166-fin`](https://github.com/cloud-itonami/cloud-itonami-iso3166-fin)),
municipality
([`cloud-itonami-municipality-fin-helsinki`](https://github.com/cloud-itonami/cloud-itonami-municipality-fin-helsinki)),
and association (this repo).

Ten entries, each carrying the page it came from
(`:source-article`) and the verbatim span the claim rests on
(`:source-quote`). Eight are first-party `ek.fi` pages; one is
BusinessEurope's own member page; two rest on Wikipedia for a
founding date `ek.fi` does not state.

**The July note that `ek.fi` "did not surface a navigable
founding-history page" is no longer true and has been removed.**
Measured 2026-09-11: `ek.fi` serves its own structure, membership,
strategy and Brussels pages, and eight of them are cited here
directly. What `ek.fi` still does not state is a founding date —
so the 1907 predecessor and the 2005 PT/TT merger remain the two
Wikipedia-sourced entries, marked `:wikipedia-corroborated` rather
than first-party.

### Reachability is not support

A URL that returns HTTP 200 without the claim reads exactly like one
that carries it, so every entry pins a verbatim span and
`--live` requires that span to still be in the fetched page.

This is not hypothetical. While this catalog was extended, a search
result offered a `finlex.fi` PDF as the official English translation
of the Associations Act (503/1989). It returned 200, from the right
first-party host — and it is the **Act on the Finnish Red Cross
(238/2000)**. It is not in this catalog because the text was read.

## Scope

A **read-only reference/archive** catalog — not an Advisor⊣Governor
actuation actor. It proposes or executes nothing on EK's behalf.

Coverage is reported honestly (see `association.facts/coverage`): an
association not in `catalog` has **no spec-basis**, full stop — never
fabricate one.

## Data

`data/datascript-tx.edn` is **the only place the facts are
authored**. Both readings are generated from it:

- `data/datascript-tx.edn` — the catalog, source of truth.
- `src/association/facts.kotoba` — the Clojure reading
  (`association.facts`). Generated.
- `src/association_facts.kotoba` — the Kotoba port, which reaches
  the Kotoba oracle, wasm and both native ISAs. Generated.
- `schema/association-rule.edn` — DataScript schema.

Query it alongside other `cloud-itonami`/`etzhayyim` compliance-fact
sources via `com-junkawasaki/root`'s
`scripts/compliance-fact-query.cljs`.

## Checking it

```
nbb scripts/verify-catalog.cljk          # structural, offline
nbb scripts/verify-catalog.cljk --live   # fetch every :url, require every
                                         # :source-quote to still be in it
nbb scripts/gen-kotoba-port.cljk         # rewrite both readings
nbb scripts/gen-kotoba-port.cljk --check # exit 1 if either has drifted
```

`verify-catalog.cljs` is three-valued on purpose: `0` checked and
clean, `1` checked with findings, `2` REFUSED — it could not check.
"I could not read the catalog" and "I read it and it was fine" must
not leave the same trace.

### The JVM suite does not run here

The owner's 2026-09-10 instruction renamed `.clj`/`.cljc` to
`.kotoba` across the workspace, deliberately, so that the compiler's
refusals would produce the work list. In this repo that means
`clojure -M:test` now collects nothing: measured 2026-09-11, **"Ran 0
tests containing 0 assertions. 0 failures, 0 errors."**, exit 0 — a
suite reporting success for running nothing — and
`association.facts` can no longer be required at all.

That state is deliberate and is **not** reverted here. It is why
both readings are generated rather than hand-maintained: the parity
suite was the only thing standing between two hand-typed copies, and
with it dormant a second copy would have nothing checking it. The
`nbb` scripts above need no JVM and do run.

## License

AGPL-3.0-or-later (matches the `cloud-itonami-iso3166-*` /
`-municipality-*` / `-assoc-*` / `-lei-*` convention). Policy text
itself remains EK's; this repo stores only citation metadata
(id/title/url/dates), not full text.
