# Changelog

## [0.10.0](https://github.com/maks-sredniawa/darek/compare/v0.9.0...v0.10.0) (2026-11-13)


### Features

* **support:** adopt the shared Help and feedback from android-starter ([c183a33](https://github.com/maks-sredniawa/darek/commit/c183a33bc6a347dc9f079eb7a688822066b24396))
* **support:** resync with android-starter for the address row, clearer report hint and neutral tip log tag ([edea3d0](https://github.com/maks-sredniawa/darek/commit/edea3d0421ef4fc2741f5469f4f3dab5dd84f124))


### Bug Fixes

* build Listening's notification in the app's language after a reboot on Android 13+ ([65e19c9](https://github.com/maks-sredniawa/darek/commit/65e19c9c5b4e64ce5891c9e66507fed252fbce21))
* refresh Listening's notification when the app language changes while it runs ([8b8a780](https://github.com/maks-sredniawa/darek/commit/8b8a7808cea80bf6f918b4bf066143781a975ed4))

## [0.9.0](https://github.com/maks-sredniawa/darek/compare/v0.8.0...v0.9.0) (2026-11-13)


### Features

* answer the name only when hailed with hej ([3fd08c1](https://github.com/maks-sredniawa/darek/commit/3fd08c13dc037819f903574298c16833d2156b46))
* listen through a microphone foreground service ([acc67c2](https://github.com/maks-sredniawa/darek/commit/acc67c2e350fafc0f4a1c9016b807235e8a354df))
* pause Listening during a call and listen again when it ends ([3c6a1ba](https://github.com/maks-sredniawa/darek/commit/3c6a1ba0999e577b4a161dd3da9d7aa912bea0e0))
* start Listening from the Quick Settings tile and after a reboot, and refuse to listen without notifications ([8b0c262](https://github.com/maks-sredniawa/darek/commit/8b0c2622cba3f474b1c6d6ffa8ddf3c7727c609c))


### Bug Fixes

* answer the name only once the hail holds still for 400 ms ([74d2a40](https://github.com/maks-sredniawa/darek/commit/74d2a40850a531b89276d217c952d19722d46263))
* give each name Decoy a whole phrase in front of every Command ([15771e8](https://github.com/maks-sredniawa/darek/commit/15771e8679e3f2ef9185c8b9dfd302cfd2116497))
* let the manifest test expect RECEIVE_BOOT_COMPLETED, which the reboot notification needs ([6a765c7](https://github.com/maks-sredniawa/darek/commit/6a765c781b7b124ea9cd30fc8093070a2209d876))
* point the persona hint and Hej comments at the Hej rule, and give ADR-0010 its consequence ([8493681](https://github.com/maks-sredniawa/darek/commit/84936812236fc5f9d4ef62ad4c0290caf1710702))
* show cannot hear and try again when the microphone cannot record, and give ADR-0010 and ADR-0011 the call pause as built ([3a824fa](https://github.com/maks-sredniawa/darek/commit/3a824fa86d725dbd075ed6142a73adb3a5aab999))
* start Listening only with the microphone granted, in the app's language ([30212fa](https://github.com/maks-sredniawa/darek/commit/30212fa655898b749cbef6be11143fde8084f9aa))

## [0.8.0](https://github.com/maksforge/darek/compare/v0.7.0...v0.8.0) (2026-11-02)


### Features

* answer the name with „No?" in place of the sound ([7486cb6](https://github.com/maksforge/darek/commit/7486cb63f2811526c3672b518ec92d299b8347d7))

## [0.7.0](https://github.com/maksforge/darek/compare/v0.6.0...v0.7.0) (2026-11-02)


### Features

* answer the name with a short sound, and let a user pause after it ([84b236c](https://github.com/maksforge/darek/commit/84b236c5abbfdd743cd357577dbca1769576b793))

## [0.6.0](https://github.com/maksforge/darek/compare/v0.5.0...v0.6.0) (2026-11-02)


### Features

* say "nie mam żadnej fuchy" when no Command is set up ([de1fdde](https://github.com/maksforge/darek/commit/de1fdde9b765d24c23aa8a69538735e2405bb8d7))

## [0.5.0](https://github.com/maksforge/darek/compare/v0.4.0...v0.5.0) (2026-11-01)


### Features

* give Daria's voice the noise settings that keep her from lisping ([5ecec1c](https://github.com/maksforge/darek/commit/5ecec1ca07cd65b23614921c4df20ab9ec3f6446))
* let the last of start, Persona switch and stop decide what Listening runs ([79c2771](https://github.com/maksforge/darek/commit/79c277123187b794424c76b3322cd005325610b6))
* let the user switch to Daria, with her own name, voice and feminine lines ([8ad3f76](https://github.com/maksforge/darek/commit/8ad3f763d5bd455e58632f31276a1df345f66f44))
* name the chosen Persona when the microphone is denied, and say Restore brings back Darek ([5c4a97a](https://github.com/maksforge/darek/commit/5c4a97aa77e3c4ee4b01b442caeaecfc96e935de))

## [0.4.0](https://github.com/maksforge/darek/compare/v0.3.0...v0.4.0) (2026-11-01)


### Features

* speak with the full-quality voice instead of its int8 quantisation ([532b6a1](https://github.com/maksforge/darek/commit/532b6a13985c8164595eb9decd260ee0ad669908))


### Bug Fixes

* put a clock hour in the case its preposition takes, o siódmej, na siódmą ([73f438e](https://github.com/maksforge/darek/commit/73f438e49f3d00435ef9062744e5f02d4a40ded2))
* read the hour after a form of godzina and in the second half of a range, and treat a no-break space as a space ([7301660](https://github.com/maksforge/darek/commit/7301660e55f1e38d15c3ca61ea7254f9aec88b89))

## [0.3.0](https://github.com/maksforge/darek/compare/v0.2.1...v0.3.0) (2026-11-01)


### Features

* ask to be set up first when Darek hears his name and nothing is configured ([460985a](https://github.com/maksforge/darek/commit/460985aa8661ee16d36ffdb01b1a459db649e79e))
* let Darek listen on Home and answer „Hej Darek, powiedz coś" out loud ([469d012](https://github.com/maksforge/darek/commit/469d0123e765253c2317b05747f42ad6e7802eb6))
* show when Darek hears his name, and say „Stary, nie rozumiem cię" when no Command follows ([71406b9](https://github.com/maksforge/darek/commit/71406b9a3d5ddbb9c3189143020e443a66674491))
* vary Darek's „nie rozumiem" answers, and bring the debug roast to Home ([e564ed9](https://github.com/maksforge/darek/commit/e564ed94e97b57a348dd6478c479dc8d46d97e84))


### Bug Fixes

* stop Listening when Home leaves, keep it through a rotation, and say when a start fails ([1794017](https://github.com/maksforge/darek/commit/1794017f9cfa11826ff6d2b3bc88c2aac22f9958))

## [0.2.1](https://github.com/maksforge/darek/compare/v0.2.0...v0.2.1) (2026-10-31)


### Bug Fixes

* switch to Polish from the first screen on Android 13 and later ([9e2e934](https://github.com/maksforge/darek/commit/9e2e934874d3cd95b996f7438196269b3de7a883))

## [0.2.0](https://github.com/maksforge/darek/compare/v0.1.0...v0.2.0) (2026-10-31)


### Features

* give Darek a cobalt and amber palette ([f224373](https://github.com/maksforge/darek/commit/f224373975ae465775627e8558fc9221c17cc835))
* open in Polish on first launch ([e31e0e6](https://github.com/maksforge/darek/commit/e31e0e6bf2df80d4e0e947f6f42516e8832f167d))
* restore default settings to Polish ([7862b6c](https://github.com/maksforge/darek/commit/7862b6c7257b999cf91edd115b6c77d5d186d3e5))


### Bug Fixes

* make Polish the listing's default language in the Play tooling ([7057f2d](https://github.com/maksforge/darek/commit/7057f2db915252f8da6074de3d143c5ae9f99056))

## Changelog

Nothing released yet.
