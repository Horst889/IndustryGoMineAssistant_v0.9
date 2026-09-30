# Industry GO Mine Assistant v0.9

Android helper prototype for Industry GO.

## v0.9 changes
- Test/dry-run mode removed: an accepted safe build is always completed.
- Strict cycle: probe -> choose highest-priority valid alternative -> first Build -> confirm dialog -> second Build -> wait for "Mine gebaut" -> next point.
- Final confirmation coordinates calibrated from the supplied 945x2048 screenshots (center ~61.5% / 58.7%).
- Main map Build fallback calibrated to ~54% / 73%.
- Build dialog detection and "kein Nexus-Verbrauch" OCR made more tolerant of line breaks.
- After a successful build, the assistant attempts to switch the top resource filter back to Uran before probing the next point.
- 96 m local-area boundary checks and 20/26/32 m mine-size checks remain enabled.

Always test while stationary first.
