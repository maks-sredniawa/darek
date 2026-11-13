# Darek

Darek is a Polish voice assistant for Android. Say *"Darek, otwórz bramę"* and he answers you out
loud and calls your gate's GSM opener, so the gate opens without touching the phone.

Everything happens on the phone: speech recognition and the spoken replies run offline, and your
voice never leaves it. Darek connects only to maksforge.com, for his page of fuchy (gigs), and to the
addresses a fucha shows you before you install it ([ADR-0008](docs/adr/0008-darek-connects-only-where-the-user-can-see.md)).
Free, no ads, no account.

## Status

Just bootstrapped from [android-starter](https://github.com/maks-sredniawa/android-starter). The release
pipeline and quality gates are real; the screens are still the template's placeholder app. Nothing
is on Play yet.

## Contributing

Darek is source-available, not open source: read it, verify it, file issues. Pull requests are not
accepted — see [LICENSE](LICENSE).

## Build

```bash
./gradlew assembleDebug          # build
./gradlew installDebug           # build + install on the connected phone
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests — needs a device
./gradlew spotlessApply          # format (the CI gate is spotlessCheck)
python3 scripts/project.py       # what the toolchain thinks this app is called
```

Commit subjects are [Conventional Commits](https://www.conventionalcommits.org); release-please
derives the version and `CHANGELOG.md` from them. See [docs/RELEASING.md](docs/RELEASING.md).
