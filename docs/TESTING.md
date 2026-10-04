# Testing

The regular suite runs without full-corpus opt-in. Fixture-dependent gates use exact files listed
in `fixtures/SHA256SUMS`; developer runs may skip unavailable proprietary fixtures, but CI release
jobs validate every hash first and fail if any file is absent.

Independent 256 MiB workers cover installed scan, compatibility scan and directed deep resolve.
The explicit `-Daa.full.deep=true` gate covers all 919 editable keys in frozen Android Auto
17.8.663814 and requires zero blocking UNKNOWN callsites. Counts are fixture-specific, not rules for
future Android Auto versions. Test oracles must never influence production resolution.
